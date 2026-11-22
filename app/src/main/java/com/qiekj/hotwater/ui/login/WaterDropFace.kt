package com.qiekj.hotwater.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class DropMood {
    IDLE,
    LOOK_DOWN,
    LOOK_UP,
    HAPPY,
    SAD
}

@Composable
fun WaterDropFace(
    mood: DropMood,
    heat: Float,
    pokeTick: Int,
    madMode: Boolean,
    swelling: Boolean,
    bubbleText: String?,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 出生弹跳长大
    val birthScale = remember { Animatable(0.18f) }
    val birthAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        birthAlpha.animateTo(1f, tween(220))
        birthScale.animateTo(1f, tween(680, easing = HotWaterMotion.SpringEasing))
    }

    // 呼吸循环
    val inf = rememberInfiniteTransition(label = "dropBreathe")
    val breatheX by inf.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breatheX"
    )
    val breatheY by inf.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breatheY"
    )

    // 随机眨眼 + 随机偷瞄
    var blinking by remember { mutableStateOf(false) }
    var glanceX by remember { mutableStateOf(0f) }
    val busyFace = mood != DropMood.IDLE || swelling

    LaunchedEffect(busyFace) {
        while (!busyFace) {
            delay(Random.nextLong(3100L, 4500L))
            if (!busyFace) {
                blinking = true
                delay(115)
                blinking = false
            }
        }
        blinking = false
    }

    LaunchedEffect(busyFace) {
        val choices = listOf(-2.8f, 2.8f, 0f, 1.9f, -1.9f)
        while (!busyFace) {
            delay(Random.nextLong(3000L, 5600L))
            if (!busyFace) {
                glanceX = choices.random()
                delay(Random.nextLong(850L, 1750L))
                glanceX = 0f
            }
        }
        glanceX = 0f
    }

    // 戳压形变 & 生气抖动
    val squashX = remember { Animatable(1f) }
    val squashY = remember { Animatable(1f) }
    val shakeX = remember { Animatable(0f) }
    val shakeRot = remember { Animatable(0f) }

    LaunchedEffect(pokeTick, madMode) {
        if (pokeTick <= 0) return@LaunchedEffect
        if (madMode) {
            val frames = listOf(-5f to -3f, 5f to 3f, -4f to -2f, 3f to 1.5f, 0f to 0f)
            for ((dx, rot) in frames) {
                coroutineScope {
                    launch { shakeX.animateTo(dx, tween(65)) }
                    launch { shakeRot.animateTo(rot, tween(65)) }
                }
            }
        } else {
            coroutineScope {
                launch {
                    squashX.animateTo(1.16f, tween(90))
                    squashX.animateTo(0.93f, tween(100))
                    squashX.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
                }
                launch {
                    squashY.animateTo(0.84f, tween(90))
                    squashY.animateTo(1.10f, tween(100))
                    squashY.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
                }
            }
        }
    }

    LaunchedEffect(mood) {
        if (mood == DropMood.HAPPY) {
            coroutineScope {
                launch {
                    squashX.animateTo(1.18f, tween(140))
                    squashX.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 360f))
                }
                launch {
                    squashY.animateTo(0.84f, tween(140))
                    squashY.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 360f))
                }
            }
        } else if (mood == DropMood.SAD) {
            for ((dx, rot) in listOf(-6f to -4f, 5f to 3f, -3f to -1.6f, 0f to 0f)) {
                coroutineScope {
                    launch { shakeX.animateTo(dx, tween(80)) }
                    launch { shakeRot.animateTo(rot, tween(80)) }
                }
            }
        }
    }

    val swellScale by animateFloatAsState(
        targetValue = if (swelling) 1.32f else 1f,
        animationSpec = tween(340, easing = HotWaterMotion.SpringEasing),
        label = "swellScale"
    )
    val eyeOffsetX by animateFloatAsState(
        targetValue = if (mood == DropMood.IDLE) glanceX else 0f,
        animationSpec = tween(360, easing = HotWaterMotion.SpringEasing),
        label = "eyeOffsetX"
    )
    val eyeOffsetY by animateFloatAsState(
        targetValue = when (mood) {
            DropMood.LOOK_UP -> -14.5f
            DropMood.LOOK_DOWN -> 3.6f
            else -> 0f
        },
        animationSpec = tween(360, easing = HotWaterMotion.SpringEasing),
        label = "eyeOffsetY"
    )
    val eyeScale by animateFloatAsState(
        targetValue = when (mood) {
            DropMood.LOOK_UP -> 0.82f
            DropMood.LOOK_DOWN -> 1.03f
            else -> 1f
        },
        animationSpec = tween(340, easing = HotWaterMotion.SpringEasing),
        label = "eyeScale"
    )
    val eyeInward by animateFloatAsState(
        targetValue = if (mood == DropMood.LOOK_UP) 1.6f else 0f,
        animationSpec = tween(340, easing = HotWaterMotion.SpringEasing),
        label = "eyeInward"
    )
    val mouthOffsetY by animateFloatAsState(
        targetValue = when (mood) {
            DropMood.LOOK_UP -> -4.8f
            DropMood.LOOK_DOWN -> 1.6f
            else -> 0f
        },
        animationSpec = tween(360, easing = HotWaterMotion.SpringEasing),
        label = "mouthOffsetY"
    )
    val blinkScaleY by animateFloatAsState(
        targetValue = if (blinking) 0.08f else 1f,
        animationSpec = tween(90),
        label = "blinkScaleY"
    )
    val fillColor by animateColorAsState(
        targetValue = lerp(HotWaterColors.Primary, HotWaterColors.EggRed, heat.coerceIn(0f, 1f)),
        animationSpec = tween(420),
        label = "dropHeatColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头顶气泡区（固定占位，防止布局跳动）
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 56.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !bubbleText.isNullOrBlank(),
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.55f, animationSpec = HotWaterMotion.bouncySpring()),
                exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.9f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 224.dp)
                            .shadow(10.dp, RoundedCornerShape(15.dp), ambientColor = HotWaterColors.Dark)
                            .clip(RoundedCornerShape(15.dp))
                            .background(HotWaterColors.Dark)
                            .padding(horizontal = 15.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = bubbleText.orEmpty(),
                            color = Color(0xFFFFF3EF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
                        val tri = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(tri, HotWaterColors.Dark)
                    }
                }
            }
        }

        // 128 x 168 水滴脸本体
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(width = 128.dp, height = 168.dp)
                .graphicsLayer {
                    alpha = birthAlpha.value
                    scaleX = birthScale.value * breatheX * squashX.value * swellScale
                    scaleY = birthScale.value * breatheY * squashY.value * swellScale
                    translationX = shakeX.value.dp.toPx()
                    rotationZ = shakeRot.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPoke
                )
        ) {
            Canvas(modifier = Modifier.size(width = 128.dp, height = 168.dp)) {
                val sx = size.width / 76f
                val sy = size.height / 100f

                // 水滴轮廓（76 x 100 坐标系）
                val dropPath = Path().apply {
                    moveTo(38f * sx, 6f * sy)
                    cubicTo(46f * sx, 32f * sy, 68f * sx, 44f * sy, 68f * sx, 62f * sy)
                    cubicTo(68f * sx, 82f * sy, 54f * sx, 94f * sy, 38f * sx, 94f * sy)
                    cubicTo(22f * sx, 94f * sy, 8f * sx, 82f * sy, 8f * sx, 62f * sy)
                    cubicTo(8f * sx, 44f * sy, 30f * sx, 32f * sy, 38f * sx, 6f * sy)
                    close()
                }
                drawPath(dropPath, color = fillColor)

                val ox = eyeOffsetX * sx
                val oy = eyeOffsetY * sy
                val inward = eyeInward * sx
                val my = mouthOffsetY * sy
                val faceColor = HotWaterColors.Bg
                val strokeStyle = Stroke(width = 2.7f * sx, cap = StrokeCap.Round)

                when (mood) {
                    DropMood.HAPPY -> {
                        val leftJoy = Path().apply {
                            moveTo((23.5f) * sx + ox, 69.5f * sy + oy)
                            quadraticTo(28f * sx + ox, 63f * sy + oy, 32.5f * sx + ox, 69.5f * sy + oy)
                        }
                        val rightJoy = Path().apply {
                            moveTo((43.5f) * sx + ox, 69.5f * sy + oy)
                            quadraticTo(48f * sx + ox, 63f * sy + oy, 52.5f * sx + ox, 69.5f * sy + oy)
                        }
                        drawPath(leftJoy, faceColor, style = strokeStyle)
                        drawPath(rightJoy, faceColor, style = strokeStyle)
                    }
                    DropMood.SAD -> {
                        drawLine(faceColor, Offset(23.5f * sx + ox, 63.5f * sy + oy), Offset(32.5f * sx + ox, 71.5f * sy + oy), 2.7f * sx, StrokeCap.Round)
                        drawLine(faceColor, Offset(32.5f * sx + ox, 63.5f * sy + oy), Offset(23.5f * sx + ox, 71.5f * sy + oy), 2.7f * sx, StrokeCap.Round)
                        drawLine(faceColor, Offset(43.5f * sx + ox, 63.5f * sy + oy), Offset(52.5f * sx + ox, 71.5f * sy + oy), 2.7f * sx, StrokeCap.Round)
                        drawLine(faceColor, Offset(52.5f * sx + ox, 63.5f * sy + oy), Offset(43.5f * sx + ox, 71.5f * sy + oy), 2.7f * sx, StrokeCap.Round)
                    }
                    else -> {
                        val rx = 3.7f * sx * eyeScale
                        val ry = 4.7f * sy * eyeScale * blinkScaleY
                        drawOval(
                            color = faceColor,
                            topLeft = Offset(28f * sx + ox + inward - rx, 67f * sy + oy - ry),
                            size = Size(rx * 2f, ry * 2f)
                        )
                        drawOval(
                            color = faceColor,
                            topLeft = Offset(48f * sx + ox - inward - rx, 67f * sy + oy - ry),
                            size = Size(rx * 2f, ry * 2f)
                        )
                    }
                }

                // 嘴部表情（仰头时略微上提并收窄，增强向上看的立体透视感）
                val mouthPath = Path().apply {
                    when (mood) {
                        DropMood.HAPPY -> {
                            moveTo(30f * sx, 79f * sy)
                            quadraticTo(38f * sx, 90f * sy, 46f * sx, 79f * sy)
                        }
                        DropMood.SAD -> {
                            moveTo(32f * sx, 84.5f * sy)
                            quadraticTo(38f * sx, 79f * sy, 44f * sx, 84.5f * sy)
                        }
                        DropMood.LOOK_UP -> {
                            moveTo(33.5f * sx, 80f * sy + my)
                            quadraticTo(38f * sx, 83.5f * sy + my, 42.5f * sx, 80f * sy + my)
                        }
                        else -> {
                            moveTo(32f * sx, 80f * sy + my)
                            quadraticTo(38f * sx, 85f * sy + my, 44f * sx, 80f * sy + my)
                        }
                    }
                }
                drawPath(mouthPath, faceColor, style = strokeStyle)
            }
        }
    }
}
