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
}
