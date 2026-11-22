package com.qiekj.hotwater.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlin.math.hypot
import kotlin.random.Random

private data class DustParticle(
    val xRatio: Float,
    val yRatio: Float,
    val dx: Float,
    val dy: Float,
    val rotDeg: Float,
    val delayFraction: Float,
    val radiusDp: Float,
    val color: Color
)

/**
 * 按区域取色（白底 / 灰字带 / 红删键）的 200 颗 Canvas 粒子粉碎动效层。
 */
@Composable
fun DustCrushOverlay(
    cardRightRatio: Float,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val particles = remember(cardRightRatio) {
        val cols = 25
        val rows = 8
        val list = ArrayList<DustParticle>(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val xr = ((c + 0.5f + (Random.nextFloat() - 0.5f) * 0.9f) / cols).coerceIn(0f, 1f)
                val yr = ((r + 0.5f + (Random.nextFloat() - 0.5f) * 0.9f) / rows).coerceIn(0f, 1f)
                val color = when {
                    xr > cardRightRatio -> HotWaterColors.Danger
                    yr in 0.22f..0.78f -> Color(0xFFC6B4AE)
                    else -> Color(0xFFF2E8E3)
                }
                val cx = xr - 0.5f
                val cy = yr - 0.5f
                val distOut = (hypot(cx, cy) / 0.72f).coerceIn(0f, 1f)
                list += DustParticle(
                    xRatio = xr,
                    yRatio = yr,
                    dx = cx * 165f + (Random.nextFloat() * 44f - 22f),
                    dy = cy * 74f + 46f + Random.nextFloat() * 58f,
                    rotDeg = (Random.nextFloat() - 0.5f) * 48f,
                    delayFraction = (Random.nextFloat() * 0.08f + distOut * 0.14f).coerceIn(0f, 0.28f),
                    radiusDp = 1.4f + Random.nextFloat() * 1.3f,
                    color = color
                )
            }
        }
        list
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(860, easing = HotWaterMotion.OutFastEasing))
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { p ->
            val localT = ((t - p.delayFraction) / (1f - p.delayFraction)).coerceIn(0f, 1f)
            if (localT >= 1f) return@forEach
            val alpha = if (localT < 0.55f) 1f else (1f - (localT - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val x = p.xRatio * size.width + p.dx.dp.toPx() * localT
            val y = p.yRatio * size.height + p.dy.dp.toPx() * localT * localT
            val scale = 1f - 0.65f * localT
            withTransform({
                rotate(degrees = p.rotDeg * localT, pivot = Offset(x, y))
            }) {
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.radiusDp.dp.toPx() * scale,
                    center = Offset(x, y)
                )
            }
        }
    }
}
