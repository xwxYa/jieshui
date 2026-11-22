package com.qiekj.hotwater.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion

enum class MorphButtonState {
    IDLE,
    LOADING,
    SUCCESS,
    DISABLED
}

@Composable
fun MorphingActionButton(
    text: String,
    state: MorphButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadingText: String = "",
    successText: String = text,
    containerColor: Color = HotWaterColors.Primary,
    contentColor: Color = Color.White,
    height: Dp = 53.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val enabled = state == MorphButtonState.IDLE
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.955f else 1f,
        animationSpec = HotWaterMotion.bouncySpring(),
        label = "btnPressScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when (state) {
            MorphButtonState.IDLE, MorphButtonState.LOADING -> containerColor
            MorphButtonState.SUCCESS, MorphButtonState.DISABLED -> HotWaterColors.DisabledBg
        },
        animationSpec = tween(280),
        label = "btnBgColor"
    )
    val fgColor by animateColorAsState(
        targetValue = when (state) {
            MorphButtonState.IDLE, MorphButtonState.LOADING -> contentColor
            MorphButtonState.SUCCESS -> HotWaterColors.Primary
            MorphButtonState.DISABLED -> HotWaterColors.DisabledFg
        },
        animationSpec = tween(260),
        label = "btnFgColor"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val fullWidth = maxWidth
        val targetWidth = when (state) {
            MorphButtonState.LOADING -> if (loadingText.isBlank()) height else minOf(fullWidth, 168.dp)
            else -> fullWidth
        }
        val animWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = HotWaterMotion.bouncySpring(),
            label = "morphWidth"
        )

        Box(
            modifier = Modifier
                .width(animWidth)
                .height(height)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(RoundedCornerShape(999.dp))
                .background(bgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically { -it / 3 })
                },
                label = "morphContent"
            ) { s ->
                when (s) {
                    MorphButtonState.LOADING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PulseSpinner(color = fgColor, size = 18.dp)
                            if (loadingText.isNotBlank()) {
                                Text(
                                    text = loadingText,
                                    color = fgColor,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    MorphButtonState.SUCCESS -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AnimatedCheckMark(color = fgColor, size = 16.dp)
                            Text(
                                text = successText,
                                color = fgColor,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    MorphButtonState.IDLE, MorphButtonState.DISABLED -> {
                        Text(
                            text = text,
                            color = fgColor,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseSpinner(color: Color, size: Dp) {
    val inf = rememberInfiniteTransition(label = "spinner")
    val angle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(780, easing = LinearEasing)),
        label = "spinAngle"
    )
    Canvas(modifier = Modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.28f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AnimatedCheckMark(color: Color, size: Dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(320, easing = HotWaterMotion.OutFastEasing))
    }
    Canvas(modifier = Modifier.size(size)) {
        val p = progress.value
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.18f, this@Canvas.size.height * 0.52f)
            val midX = this@Canvas.size.width * 0.42f
            val midY = this@Canvas.size.height * 0.76f
            val endX = this@Canvas.size.width * 0.84f
            val endY = this@Canvas.size.height * 0.26f
            if (p <= 0.45f) {
                val t = p / 0.45f
                lineTo(
                    this@Canvas.size.width * 0.18f + (midX - this@Canvas.size.width * 0.18f) * t,
                    this@Canvas.size.height * 0.52f + (midY - this@Canvas.size.height * 0.52f) * t
                )
            } else {
                lineTo(midX, midY)
                val t = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                lineTo(midX + (endX - midX) * t, midY + (endY - midY) * t)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
