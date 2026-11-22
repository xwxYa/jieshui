package com.qiekj.hotwater.ui.orders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.domain.OrderSummaryStats
import com.qiekj.hotwater.ui.components.RollingMoneyText
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.delay

/**
 * 消费汇总页：展示近 6 个月消费柱状图与设备使用排行。
 */
@Composable
fun SummaryScreen(
    stats: OrderSummaryStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = HotWaterColors.OnSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "消费汇总",
                fontSize = 19.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HotWaterColors.OnSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 30.dp)
        ) {
            item {
                RollingMoneyText(
                    formatted = "%.2f".format(stats.totalAmount),
                    fontSize = 36.sp,
                    color = HotWaterColors.OnSurface,
                    prefix = "¥",
                    prefixFontSize = 18.sp,
                    prefixColor = HotWaterColors.Muted,
                    playKey = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "累计热水支出（已剔除 0 元单）",
                    fontSize = 11.5.sp,
                    color = HotWaterColors.Muted
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetricCard(
                        label = "接水次数",
                        value = "${stats.totalCount} 次",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        label = "单次均价",
                        value = "¥${"%.2f".format(stats.avgAmount)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(15.dp), ambientColor = Color(0x188F4C38))
                        .clip(RoundedCornerShape(15.dp))
                        .background(HotWaterColors.Surface)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最常去的机器",
                        fontSize = 11.sp,
                        color = HotWaterColors.Hint
                    )
                    Text(
                        text = stats.topDeviceName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HotWaterColors.OnSurface
                    )
                }
            }

            // 近 6 个月柱状图
            item {
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "近 6 个月",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HotWaterColors.Muted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "真实月度汇总",
                        fontSize = 11.sp,
                        color = Color(0xFFC4B4AE)
                    )
                }
                Spacer(Modifier.height(14.dp))

                val delays = listOf(0L, 70L, 30L, 130L, 180L, 100L)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    stats.recentSixMonths.forEachIndexed { idx, point ->
                        val grow = remember(point.yearMonth) { Animatable(0f) }
                        LaunchedEffect(point.yearMonth, point.ratio) {
                            delay(delays.getOrElse(idx) { 0L })
                            grow.animateTo(point.ratio, tween(480, easing = HotWaterMotion.SpringEasing))
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (point.amount > 0.0) {
                                Text(
                                    text = "¥${"%.1f".format(point.amount)}",
                                    fontSize = 9.5.sp,
                                    color = if (point.isMax) HotWaterColors.Primary else HotWaterColors.Hint,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(3.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                            scaleY = grow.value.coerceAtLeast(0.05f)
                                        }
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        .background(if (point.isMax) HotWaterColors.Primary else Color(0xFFE2D3CE))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    stats.recentSixMonths.forEach { point ->
                        Text(
                            text = point.label,
                            fontSize = 10.5.sp,
                            color = if (point.isMax) HotWaterColors.Primary else HotWaterColors.Hint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 设备排行
            if (stats.deviceRanking.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = "设备排行",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HotWaterColors.Muted
                    )
                    Spacer(Modifier.height(8.dp))

                    stats.deviceRanking.forEachIndexed { idx, row ->
                        val barAnim = remember(row.deviceName) { Animatable(0f) }
                        LaunchedEffect(row.deviceName, row.ratio) {
                            delay(140L + idx * 80L)
                            barAnim.animateTo(row.ratio, tween(520, easing = HotWaterMotion.SpringEasing))
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.deviceName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HotWaterColors.OnSurface
                                )
                                Text(
                                    text = "¥${"%.2f".format(row.amount)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HotWaterColors.Primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFF1E4E0))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(barAnim.value.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(HotWaterColors.PrimaryDim)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${row.percent}% · ${row.count} 次",
                                fontSize = 10.5.sp,
                                color = HotWaterColors.Hint
                            )
                        }
                        if (idx < stats.deviceRanking.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF6ECE9))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(15.dp), ambientColor = Color(0x188F4C38))
            .clip(RoundedCornerShape(15.dp))
            .background(HotWaterColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = HotWaterColors.Hint
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = HotWaterColors.OnSurface
        )
    }
}
