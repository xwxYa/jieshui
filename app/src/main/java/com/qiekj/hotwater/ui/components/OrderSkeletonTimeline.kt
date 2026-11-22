package com.qiekj.hotwater.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qiekj.hotwater.ui.theme.HotWaterColors

@Composable
fun OrderSkeletonTimeline(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val shift by inf.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerShift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFFF3E7E3), Color(0xFFFAF3F0), Color(0xFFF3E7E3)),
        start = Offset(shift, 0f),
        end = Offset(shift + 340f, 180f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(126.dp).height(34.dp).clip(RoundedCornerShape(10.dp)).background(brush))
                Box(Modifier.width(168.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            }
            Box(Modifier.width(56.dp).height(76.dp).clip(RoundedCornerShape(12.dp)).background(brush))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(HotWaterColors.OutlineVar))

        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(brush))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                    Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                }
                Box(Modifier.width(48.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
    }
}
