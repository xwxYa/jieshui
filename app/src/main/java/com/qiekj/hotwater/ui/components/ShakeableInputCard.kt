package com.qiekj.hotwater.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qiekj.hotwater.ui.theme.HotWaterColors

@Composable
fun ShakeableInputCard(
    shakeTick: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            glowAlpha.snapTo(1f)
            val steps = listOf(-10f, 9f, -7f, 5f, -2.5f, 0f)
            for (target in steps) {
                offsetX.animateTo(target, tween(45, easing = LinearEasing))
            }
            glowAlpha.animateTo(0f, tween(420))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.dp.roundToPx(), 0) }
            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x208F4C38), spotColor = Color(0x188F4C38))
            .clip(RoundedCornerShape(14.dp))
            .background(HotWaterColors.Surface)
            .border(
                width = 1.4.dp,
                color = HotWaterColors.Danger.copy(alpha = glowAlpha.value * 0.72f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}
