package com.qiekj.hotwater

import com.qiekj.hotwater.data.model.OrderItem
import com.qiekj.hotwater.domain.OrderAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class OrderAnalyticsTest {

    @Test
    fun `analytics filters zero orders and computes real 6-month bars`() {
        val items = listOf(
            OrderItem("1", "1号楼西热水", 0.0, "2026-03-18 07:00:00", "2026-03-18", "3月18日", "07:00"),
            OrderItem("2", "1号楼西热水", 1.50, "2026-03-18 08:00:00", "2026-03-18", "3月18日", "08:00"),
            OrderItem("3", "2号楼东热水", 0.50, "2026-03-17 09:00:00", "2026-03-17", "3月17日", "09:00"),
            OrderItem("4", "1号楼西热水", 4.00, "2026-01-10 10:00:00", "2026-01-10", "1月10日", "10:00"),
            OrderItem("5", "2号楼东热水", 2.00, "2025-11-05 11:00:00", "2025-11-05", "11月5日", "11:00")
        )

        val stats = OrderAnalytics.analyze(items, anchorMonth = YearMonth.of(2026, 3))

        assertEquals(8.0, stats.totalAmount, 0.001)
        assertEquals(4, stats.totalCount)
        assertEquals(2.0, stats.avgAmount, 0.001)
        assertEquals("1号楼西热水", stats.topDeviceName)
        assertEquals(2, stats.years.size)
        assertEquals(2026, stats.years[0].year)
        assertEquals(6.0, stats.years[0].totalAmount, 0.001)
        assertEquals(2025, stats.years[1].year)
        assertEquals(2.0, stats.years[1].totalAmount, 0.001)

        // 近 6 个月：2025-10 ~ 2026-03，其中 2026-01 最高 (4.00)
        assertEquals(6, stats.recentSixMonths.size)
        assertEquals(listOf("10月", "11月", "12月", "1月", "2月", "3月"), stats.recentSixMonths.map { it.label })
        val jan = stats.recentSixMonths.first { it.label == "1月" }
        assertTrue(jan.isMax)
        assertEquals(1f, jan.ratio, 0.001f)
        val mar = stats.recentSixMonths.first { it.label == "3月" }
        assertEquals(2.0, mar.amount, 0.001)
        assertEquals(0.5f, mar.ratio, 0.001f)
    }

    @Test
    fun `empty order list does not produce NaN`() {
        val stats = OrderAnalytics.analyze(emptyList(), anchorMonth = YearMonth.of(2026, 3))
        assertEquals(0.0, stats.totalAmount, 0.001)
        assertEquals(0.0, stats.avgAmount, 0.001)
        assertEquals("还没有", stats.topDeviceName)
        assertEquals(6, stats.recentSixMonths.size)
    }
}
