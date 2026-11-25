package com.qiekj.hotwater

import com.qiekj.hotwater.data.local.OrdersCache
import com.qiekj.hotwater.data.model.OrderItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OrdersCacheTest {

    @Test
    fun `concurrent writes remain intact and truncated cache degrades to null`() = runTest {
        val dir = Files.createTempDirectory("orders_cache_test").toFile()
        try {
            val cache = OrdersCache(dir)
            val sample = listOf(
                OrderItem("o1", "1号楼", 1.2, "2026-03-18 07:00:00", "2026-03-18", "3月18日", "07:00")
            )

            // 12 次并发写入模拟“结束接水”同时触发多次刷单
            (1..12).map { i ->
                async {
                    cache.write(sample + OrderItem("o_$i", "2号楼", i * 0.5, "2026-03-18 08:00:00", "2026-03-18", "3月18日", "08:00"))
                }
            }.awaitAll()

            val readBack = cache.read()
            assertNotNull(readBack)
            assertEquals(2, readBack!!.size)

            // 写入截断损坏文件应返回 null 而不是抛异常
            File(dir, "orders_native.json").writeText("""[{"orderNo":"broken"""")
            assertNull(cache.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `mergeAndWrite preserves older archived orders when server window drops them`() = runTest {
        val dir = Files.createTempDirectory("orders_merge_test").toFile()
        try {
            val cache = OrdersCache(dir)
            val oldArchive = listOf(
                OrderItem("o_2023", "1号楼", 0.50, "2023-09-01 08:00:00", "2023-09-01", "9月1日", "08:00"),
                OrderItem("o_2026_a", "2号楼", 0.80, "2026-03-10 12:00:00", "2026-03-10", "3月10日", "12:00")
            )
            cache.write(oldArchive)

            // 服务端只返回了近期的 2 条（2023 年老单已滑出服务端窗口），其中 o_2026_a 金额更新
            val serverPage = listOf(
                OrderItem("o_2026_b", "3号楼", 1.10, "2026-03-18 19:30:00", "2026-03-18", "3月18日", "19:30"),
                OrderItem("o_2026_a", "2号楼", 0.90, "2026-03-10 12:00:00", "2026-03-10", "3月10日", "12:00")
            )
            val merged = cache.mergeAndWrite(serverPage)

            assertEquals(3, merged.size)
            assertEquals("o_2026_b", merged[0].orderNo)
            assertEquals("o_2026_a", merged[1].orderNo)
            assertEquals(0.90, merged[1].payPrice, 0.001)
            assertEquals("o_2023", merged[2].orderNo)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `account scoped archives stay isolated and legacy file migrates cleanly`() = runTest {
        val dir = Files.createTempDirectory("orders_account_test").toFile()
        try {
            // 模拟旧版单文件归档
            val legacyCache = OrdersCache(dir)
            legacyCache.write(
                listOf(OrderItem("o_a1", "1号楼", 0.6, "2025-10-01 08:00:00", "2025-10-01", "10月1日", "08:00"))
            )

            var currentPhone = "13800001111"
            val scopedCache = OrdersCache(dir, accountProvider = { currentPhone })

            // 账号 A 读取时自动承接旧单文件
            val readA = scopedCache.read()
            assertNotNull(readA)
            assertEquals(1, readA!!.size)
            assertEquals("o_a1", readA[0].orderNo)

            // 切到账号 B，读不到账号 A 的账单，写入 B 也不会污染 A
            currentPhone = "13900002222"
            assertNull(scopedCache.read())
            scopedCache.mergeAndWrite(
                listOf(OrderItem("o_b1", "9号楼", 1.5, "2026-03-01 12:00:00", "2026-03-01", "3月1日", "12:00"))
            )

            // 切回账号 A，依然只有 A 自己的账单
            currentPhone = "13800001111"
            val readAAgain = scopedCache.read()
            assertEquals(1, readAAgain!!.size)
            assertEquals("o_a1", readAAgain[0].orderNo)
        } finally {
            dir.deleteRecursively()
        }
    }
}
