package com.qiekj.hotwater.ui.orders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.theme.HotWaterColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 订单空状态：「睡觉的水滴」—— 点击它会睁眼眨两下再睡回去。
 */
@Composable
internal fun SleepingDropEmptyState(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var awake by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(false) }
    val squashX = remember { Animatable(1f) }
    val squashY = remember { Animatable(1f) }

    val inf = rememberInfiniteTransition(label = "sleepBreathe")
    val breathe by inf.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sleepScale"
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(width = 76.dp, height = 100.dp)
                .graphicsLayer {
                    val b = if (awake) 1f else breathe
                    scaleX = b * squashX.value
                    scaleY = b * squashY.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    scope.launch {
                        awake = true
                        launch {
                            squashX.animateTo(0.95f, tween(160))
                            squashY.animateTo(1.15f, tween(160))
                            squashX.animateTo(1.14f, tween(160))
                            squashY.animateTo(0.96f, tween(160))
                            squashX.animateTo(1f, tween(180))
                            squashY.animateTo(1f, tween(180))
                        }
                        delay(180)
                        repeat(2) {
                            blink = true
                            delay(110)
                            blink = false
                            delay(170)
                        }
                        delay(760)
                        awake = false
                    }
                }
        ) {
            val sx = size.width / 76f
            val sy = size.height / 100f
            val p = Path().apply {
                moveTo(38f * sx, 6f * sy)
                cubicTo(46f * sx, 32f * sy, 68f * sx, 44f * sy, 68f * sx, 62f * sy)
                cubicTo(68f * sx, 82f * sy, 54f * sx, 94f * sy, 38f * sx, 94f * sy)
                cubicTo(22f * sx, 94f * sy, 8f * sx, 82f * sy, 8f * sx, 62f * sy)
                cubicTo(8f * sx, 44f * sy, 30f * sx, 32f * sy, 38f * sx, 6f * sy)
                close()
            }
            drawPath(p, if (awake) HotWaterColors.PrimaryLite else Color(0xFFDCCCC7))

            if (!awake) {
                drawLine(Color(0xFFB7A29C), Offset(24f * sx, 66f * sy), Offset(33f * sx, 66f * sy), 2.6f * sx, StrokeCap.Round)
                drawLine(Color(0xFFB7A29C), Offset(43f * sx, 66f * sy), Offset(52f * sx, 66f * sy), 2.6f * sx, StrokeCap.Round)
            } else {
                val ry = if (blink) 0.5f * sy else 3.4f * sy
                drawOval(
                    color = Color(0xFF7E6C66),
                    topLeft = Offset((28.5f - 3.4f) * sx, 66f * sy - ry),
                    size = Size(6.8f * sx, ry * 2f)
                )
                drawOval(
                    color = Color(0xFF7E6C66),
                    topLeft = Offset((47.5f - 3.4f) * sx, 66f * sy - ry),
                    size = Size(6.8f * sx, ry * 2f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "…还没接过水",
            fontSize = 12.5.sp,
            color = HotWaterColors.Hint
        )
    }
}
