package com.qiekj.hotwater.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.MainTab
import com.qiekj.hotwater.ui.theme.HotWaterColors

@Composable
fun SpringTabBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HotWaterColors.Surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HotWaterColors.OutlineVar)
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 7.dp)
                .height(48.dp)
        ) {
            val slotWidth = maxWidth / 2
            val pillWidth = minOf(slotWidth - 16.dp, 132.dp)
            val pillOffset by animateDpAsState(
                targetValue = if (selectedTab == MainTab.DEVICES) {
                    (slotWidth - pillWidth) / 2
                } else {
                    slotWidth + (slotWidth - pillWidth) / 2
                },
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
                label = "tabPillOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HotWaterColors.PillTint)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                TabItemButton(
                    label = "设备",
                    selected = selectedTab == MainTab.DEVICES,
                    isDropIcon = true,
                    onClick = { onSelect(MainTab.DEVICES) },
                    modifier = Modifier.weight(1f)
                )
                TabItemButton(
                    label = "订单",
                    selected = selectedTab == MainTab.ORDERS,
                    isDropIcon = false,
                    onClick = { onSelect(MainTab.ORDERS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItemButton(
    label: String,
    selected: Boolean,
    isDropIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jelly = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            jelly.snapTo(0.86f)
            jelly.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 500f))
        }
    }
    val tint by animateColorAsState(
        targetValue = if (selected) HotWaterColors.Primary else Color(0xFFAD9C96),
        animationSpec = tween(200),
        label = "tabTint"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer {
                    scaleX = jelly.value
                    scaleY = jelly.value
                }
        ) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (isDropIcon) {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.13f)
                    cubicTo(w * 0.65f, h * 0.38f, w * 0.78f, h * 0.48f, w * 0.78f, h * 0.62f)
                    cubicTo(w * 0.78f, h * 0.79f, w * 0.65f, h * 0.90f, w * 0.5f, h * 0.90f)
                    cubicTo(w * 0.35f, h * 0.90f, w * 0.22f, h * 0.79f, w * 0.22f, h * 0.62f)
                    cubicTo(w * 0.22f, h * 0.48f, w * 0.35f, h * 0.38f, w * 0.5f, h * 0.13f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            } else {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.17f, h * 0.13f),
                    size = Size(w * 0.66f, h * 0.74f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.35f, h * 0.35f), Offset(w * 0.65f, h * 0.35f), 1.9.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.35f, h * 0.52f), Offset(w * 0.65f, h * 0.52f), 1.9.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.35f, h * 0.69f), Offset(w * 0.53f, h * 0.69f), 1.9.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}
