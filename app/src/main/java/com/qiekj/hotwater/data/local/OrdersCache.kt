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
    private val cacheDir: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DEFAULT_TTL_MS = 6L * 3600_000L
    }

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun targetFile() = File(cacheDir, "orders_native.json")

    suspend fun read(): List<OrderItem>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = targetFile()
                if (!file.exists() || file.length() <= 2L) return@withContext null
                val raw = file.readText(Charsets.UTF_8)
                if (!raw.trimStart().startsWith("[")) return@withContext null
                json.decodeFromString<List<OrderItem>>(raw)
            }.getOrNull()
        }
    }

    fun isFresh(): Boolean = runCatching {
        val file = targetFile()
        file.exists() && (nowProvider() - file.lastModified()) < ttlMs
    }.getOrDefault(false)

    suspend fun write(items: List<OrderItem>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val encoded = json.encodeToString(items)
                val tmp = File(cacheDir, "orders_native.${UUID.randomUUID()}.tmp")
                tmp.writeText(encoded, Charsets.UTF_8)
                val dst = targetFile()
                if (!tmp.renameTo(dst)) {
                    dst.writeText(encoded, Charsets.UTF_8)
                    tmp.delete()
                }
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { targetFile().delete() }
        }
    }
}
