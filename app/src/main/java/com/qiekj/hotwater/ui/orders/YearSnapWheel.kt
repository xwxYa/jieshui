package com.qiekj.hotwater.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.domain.YearOrderGroup
import com.qiekj.hotwater.ui.theme.HotWaterColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 3 项高度 132dp 原生吸附年份滚轮，按距中心距离连续计算缩放与透明度（「小 大 小」），
 * 抬手吸附稳定后再切换年份。
 */
@Composable
internal fun YearSnapWheel(
    years: List<YearOrderGroup>,
    selectedYear: Int?,
    onSelectYear: (Int) -> Unit
) {
    if (years.isEmpty()) return
    val scope = rememberCoroutineScope()
    val initIndex = years.indexOfFirst { it.year == selectedYear }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val wheelNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    val centerIndex by remember(years) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val midY = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - midY) }
                ?.index
                ?.coerceIn(0, years.lastIndex)
                ?: initIndex
        }
    }

    LaunchedEffect(listState.isScrollInProgress, centerIndex) {
        if (!listState.isScrollInProgress) {
            delay(120)
            val target = years.getOrNull(centerIndex)?.year
            if (target != null && target != selectedYear) {
                onSelectYear(target)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(74.dp)
            .height(132.dp)
            .nestedScroll(wheelNestedScroll)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = 44.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(years.size, key = { years[it].year }) { idx ->
                val y = years[idx].year
                val k by remember {
                    derivedStateOf {
                        val layout = listState.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == idx }
                        if (item == null || item.size == 0) {
                            if (idx == centerIndex) 1f else 0f
                        } else {
                            val midY = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                            val itemMid = item.offset + item.size / 2f
                            val dist = abs(itemMid - midY) / item.size.toFloat()
                            (1f - dist * 0.62f).coerceIn(0f, 1f)
                        }
                    }
                }
                val scale = 0.78f + k * 0.38f
                val alpha = 0.28f + k * 0.72f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch { listState.animateScrollToItem(idx) }
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$y",
                        fontSize = 15.sp,
                        fontWeight = if (idx == centerIndex) FontWeight.Bold else FontWeight.SemiBold,
                        color = HotWaterColors.Primary.copy(alpha = alpha)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "年",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HotWaterColors.Primary.copy(alpha = alpha * 0.72f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HotWaterColors.Bg, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, HotWaterColors.Bg)
                    )
                )
        )
    }
}
