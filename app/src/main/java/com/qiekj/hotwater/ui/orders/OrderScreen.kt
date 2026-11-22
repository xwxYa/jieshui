package com.qiekj.hotwater.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.OrdersUiState
import com.qiekj.hotwater.ui.components.OrderSkeletonTimeline
import com.qiekj.hotwater.ui.components.RollingMoneyText
import com.qiekj.hotwater.ui.theme.HotWaterColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    ordersState: OrdersUiState,
    onRefreshOrders: () -> Unit,
    onSelectYear: (Int) -> Unit,
    onToggleMonth: (Int) -> Unit,
    onOpenSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = ordersState.orderStats
    val activeYear = stats.years.firstOrNull { it.year == ordersState.selectedYear } ?: stats.years.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
            .statusBarsPadding()
    ) {
        Text(
            text = "订单",
            fontSize = 19.5.sp,
            fontWeight = FontWeight.Bold,
            color = HotWaterColors.OnSurface,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 12.dp)
        )

        PullToRefreshBox(
            isRefreshing = ordersState.ordersRefreshing,
            onRefresh = onRefreshOrders,
            modifier = Modifier.weight(1f)
        ) {
            when {
                ordersState.ordersLoading && stats.totalCount == 0 -> {
                    OrderSkeletonTimeline(modifier = Modifier.fillMaxSize())
                }
                stats.totalCount == 0 -> {
                    SleepingDropEmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)
                    ) {
                        item(key = "order_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onOpenSummary
                                        )
                                ) {
                                    RollingMoneyText(
                                        formatted = "%.2f".format(stats.totalAmount),
                                        fontSize = 34.sp,
                                        color = HotWaterColors.OnSurface,
                                        prefix = "¥",
                                        prefixFontSize = 20.sp,
                                        prefixColor = HotWaterColors.Muted,
                                        playKey = 1
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "全部 · ${stats.totalDays} 天 · ${stats.totalCount} 单",
                                            fontSize = 11.5.sp,
                                            color = HotWaterColors.Muted
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(HotWaterColors.MoreChipBg)
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "看汇总 ›",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = HotWaterColors.PrimaryDim
                                            )
                                        }
                                    }
                                }

                                YearSnapWheel(
                                    years = stats.years,
                                    selectedYear = activeYear?.year,
                                    onSelectYear = onSelectYear
                                )
                            }
                        }

                        if (activeYear != null) {
                            item(key = "year_subtotal") {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${activeYear.year} 年",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HotWaterColors.Primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${activeYear.dayCount} 天 · ${activeYear.totalCount} 单",
                                            fontSize = 11.sp,
                                            color = HotWaterColors.Muted
                                        )
                                        Spacer(Modifier.weight(1f))
                                        RollingMoneyText(
                                            formatted = "%.2f".format(activeYear.totalAmount),
                                            fontSize = 13.5.sp,
                                            color = HotWaterColors.Primary,
                                            prefix = "¥",
                                            prefixFontSize = 12.sp,
                                            prefixColor = HotWaterColors.Primary,
                                            playKey = 0
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(HotWaterColors.OutlineVar)
                                    )
                                }
                            }

                            items(
                                items = activeYear.months,
                                key = { "${it.year}_${it.month}" }
                            ) { monthGroup ->
                                MonthAccordionSection(
                                    group = monthGroup,
                                    expanded = monthGroup.month in ordersState.expandedMonths,
                                    onToggle = { onToggleMonth(monthGroup.month) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
