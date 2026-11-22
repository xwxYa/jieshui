package com.qiekj.hotwater.domain

import com.qiekj.hotwater.data.model.OrderItem
import java.time.YearMonth
import kotlin.math.roundToInt

data class DayOrderGroup(
    val dateKey: String,
    val dateLabel: String,
    val year: Int,
    val month: Int,
    val items: List<OrderItem>,
    val totalAmount: Double
)

data class MonthOrderGroup(
    val year: Int,
    val month: Int,
    val days: List<DayOrderGroup>,
    val totalCount: Int,
    val totalAmount: Double
)

data class YearOrderGroup(
    val year: Int,
    val months: List<MonthOrderGroup>,
    val dayCount: Int,
    val totalCount: Int,
    val totalAmount: Double
)

data class MonthlyBarPoint(
    val yearMonth: YearMonth,
    val label: String,
    val amount: Double,
    val ratio: Float,
    val isMax: Boolean
)

data class DeviceRankItem(
    val deviceName: String,
    val amount: Double,
    val count: Int,
    val ratio: Float,
    val percent: Int
)

data class OrderSummaryStats(
    val totalAmount: Double,
    val totalCount: Int,
    val totalDays: Int,
    val avgAmount: Double,
    val topDeviceName: String,
    val recentSixMonths: List<MonthlyBarPoint>,
    val deviceRanking: List<DeviceRankItem>,
    val years: List<YearOrderGroup>
)

object OrderAnalytics {
    private fun roundMoney(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    fun analyze(
        rawOrders: List<OrderItem>,
        anchorMonth: YearMonth = YearMonth.now()
    ): OrderSummaryStats {
        val paidOrders = rawOrders.filter { item ->
            if (item.payPrice <= 0.0) return@filter false
            val parts = item.dateKey.split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            y > 0 && m in 1..12
        }

        val dayGroups = paidOrders
            .groupBy { it.dateKey }
            .entries
            .sortedByDescending { it.key }
            .map { (dateKey, items) ->
                val parts = dateKey.split("-")
                val y = parts[0].toInt()
                val m = parts[1].toInt()
                val sortedItems = items.sortedByDescending { it.createTime }
                DayOrderGroup(
                    dateKey = dateKey,
                    dateLabel = sortedItems.first().dateLabel,
                    year = y,
                    month = m,
                    items = sortedItems,
                    totalAmount = roundMoney(sortedItems.sumOf { it.payPrice })
                )
            }

        val yearGroups = dayGroups
            .groupBy { it.year }
            .entries
            .sortedByDescending { it.key }
            .map { (year, daysInYear) ->
                val months = daysInYear
                    .groupBy { it.month }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (month, daysInMonth) ->
                        MonthOrderGroup(
                            year = year,
                            month = month,
                            days = daysInMonth,
                            totalCount = daysInMonth.sumOf { it.items.size },
                            totalAmount = roundMoney(daysInMonth.sumOf { it.totalAmount })
                        )
                    }
                YearOrderGroup(
                    year = year,
                    months = months,
                    dayCount = daysInYear.size,
                    totalCount = months.sumOf { it.totalCount },
                    totalAmount = roundMoney(months.sumOf { it.totalAmount })
                )
            }

        val totalAmount = roundMoney(paidOrders.sumOf { it.payPrice })
        val totalCount = paidOrders.size
        val avgAmount = if (totalCount > 0) roundMoney(totalAmount / totalCount) else 0.0

        val byDevice = paidOrders.groupBy { it.machineName.ifBlank { "未知设备" } }
        val ranking = byDevice.entries
            .map { (name, list) ->
                val amt = roundMoney(list.sumOf { it.payPrice })
                val ratio = if (totalAmount > 0.0) (amt / totalAmount).toFloat().coerceIn(0f, 1f) else 0f
                DeviceRankItem(
                    deviceName = name,
                    amount = amt,
                    count = list.size,
                    ratio = ratio,
                    percent = (ratio * 100f).roundToInt()
                )
            }
            .sortedByDescending { it.amount }

        val effectiveAnchor = if (paidOrders.isNotEmpty()) {
            val latestDay = dayGroups.first()
            val latestYm = YearMonth.of(latestDay.year, latestDay.month)
            if (latestYm.isAfter(anchorMonth)) latestYm else anchorMonth
        } else {
            anchorMonth
        }

        val monthlyTotals = HashMap<YearMonth, Double>()
        dayGroups.forEach { d ->
            val ym = YearMonth.of(d.year, d.month)
            monthlyTotals[ym] = roundMoney((monthlyTotals[ym] ?: 0.0) + d.totalAmount)
        }

        val lastSix = (5 downTo 0).map { offset -> effectiveAnchor.minusMonths(offset.toLong()) }
        val maxMonthly = lastSix.maxOfOrNull { monthlyTotals[it] ?: 0.0 } ?: 0.0
        val recentSixMonths = lastSix.map { ym ->
            val amt = monthlyTotals[ym] ?: 0.0
            val ratio = if (maxMonthly > 0.0) (amt / maxMonthly).toFloat().coerceIn(0.04f, 1f) else 0.04f
            MonthlyBarPoint(
                yearMonth = ym,
                label = "${ym.monthValue}月",
                amount = amt,
                ratio = ratio,
                isMax = maxMonthly > 0.0 && amt == maxMonthly
            )
        }

        return OrderSummaryStats(
            totalAmount = totalAmount,
            totalCount = totalCount,
            totalDays = dayGroups.size,
            avgAmount = avgAmount,
            topDeviceName = ranking.firstOrNull()?.deviceName ?: "还没有",
            recentSixMonths = recentSixMonths,
            deviceRanking = ranking,
            years = yearGroups
        )
    }
}
