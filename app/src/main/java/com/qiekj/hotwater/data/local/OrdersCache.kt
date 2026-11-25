package com.qiekj.hotwater.data.local

import com.qiekj.hotwater.data.model.OrderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class OrdersCache(
    private val storageDir: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val accountProvider: () -> String = { "" }
) {
    companion object {
        const val DEFAULT_TTL_MS = 6L * 3600_000L

        fun orderKey(item: OrderItem): String {
            return item.orderNo.trim().ifBlank {
                "${item.createTime}|${item.machineName}|${"%.2f".format(item.payPrice)}"
            }
        }

        /**
         * 只增不减地合并本地历史归档与服务端新拉取账单：
         * - 服务端已滑出查询窗口的老订单永久保留；
         * - 同单号记录用服务端最新数据覆盖更新；
         * - 结果按 createTime 倒序排列。
         */
        fun mergeOrders(existing: List<OrderItem>, incoming: List<OrderItem>): List<OrderItem> {
            if (existing.isEmpty()) return incoming.sortedByDescending { it.createTime }
            if (incoming.isEmpty()) return existing.sortedByDescending { it.createTime }
            val merged = LinkedHashMap<String, OrderItem>(existing.size + incoming.size)
            for (item in existing) {
                merged[orderKey(item)] = item
            }
            for (item in incoming) {
                merged[orderKey(item)] = item
            }
            return merged.values.sortedByDescending { it.createTime }
        }
    }

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun accountTag(): String {
        val raw = accountProvider().trim().filter { it.isLetterOrDigit() }
        return if (raw.isBlank()) "default" else raw
    }

    private fun legacyFile() = File(storageDir, "orders_native.json")

    private fun targetFile(): File {
        val tag = accountTag()
        return if (tag == "default") legacyFile() else File(storageDir, "orders_native_$tag.json")
    }

    private fun fullSyncMarkerFile(): File {
        val tag = accountTag()
        return File(storageDir, "orders_fullsync_$tag.flag")
    }

    suspend fun read(): List<OrderItem>? = mutex.withLock {
        readLocked()
    }

    fun isFresh(): Boolean = runCatching {
        val file = resolveReadableFile()
        file != null && (nowProvider() - file.lastModified()) < ttlMs
    }.getOrDefault(false)

    fun hasCompletedFullSync(): Boolean = runCatching {
        fullSyncMarkerFile().exists()
    }.getOrDefault(false)

    suspend fun markFullSyncCompleted() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!storageDir.exists()) storageDir.mkdirs()
                fullSyncMarkerFile().writeText(nowProvider().toString(), Charsets.UTF_8)
            }
        }
    }

    suspend fun write(items: List<OrderItem>) = mutex.withLock {
        writeLocked(items)
    }

    suspend fun mergeAndWrite(incoming: List<OrderItem>): List<OrderItem> = mutex.withLock {
        val existing = readLocked().orEmpty()
        val merged = mergeOrders(existing, incoming)
        writeLocked(merged)
        merged
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                targetFile().delete()
                fullSyncMarkerFile().delete()
            }
        }
    }

    private fun resolveReadableFile(): File? {
        val dst = targetFile()
        if (dst.exists() && dst.length() > 2L) return dst
        val legacy = legacyFile()
        if (dst != legacy && legacy.exists() && legacy.length() > 2L) {
            // 仅在旧单文件尚未迁移时无缝承接到当前账号文件，避免老用户升级丢归档
            val migrated = runCatching {
                legacy.renameTo(dst)
            }.getOrDefault(false)
            return if (migrated && dst.exists()) dst else legacy
        }
        return null
    }

    private suspend fun readLocked(): List<OrderItem>? = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveReadableFile() ?: return@withContext null
            val raw = file.readText(Charsets.UTF_8)
            if (!raw.trimStart().startsWith("[")) return@withContext null
            json.decodeFromString<List<OrderItem>>(raw)
        }.getOrNull()
    }

    private suspend fun writeLocked(items: List<OrderItem>) = withContext(Dispatchers.IO) {
        runCatching {
            if (!storageDir.exists()) storageDir.mkdirs()
            val encoded = json.encodeToString(items)
            val tmp = File(storageDir, "orders_native.${UUID.randomUUID()}.tmp")
            tmp.writeText(encoded, Charsets.UTF_8)
            val dst = targetFile()
            if (!tmp.renameTo(dst)) {
                dst.writeText(encoded, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}
