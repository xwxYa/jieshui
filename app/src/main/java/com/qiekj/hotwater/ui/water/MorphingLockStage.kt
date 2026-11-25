package com.qiekj.hotwater.ui.water

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.qiekj.hotwater.ui.WaterPhase
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 接水页核心舞台：锁体通过四段三次贝塞尔控制点插值变形为水滴，
 * 包含旋转虚线环、唤醒进度弧、锁环弹飞、水花波纹与水滴眨眼张望。
 */
@Composable
fun MorphingLockStage(
    phase: WaterPhase,
    modifier: Modifier = Modifier
) {
    val isWaking = phase == WaterPhase.WAKING
    val isDone = phase == WaterPhase.UNLOCKED || phase == WaterPhase.DISPENSING

    // 1. 按需运行无限动画：空闲仅跑慢速虚线旋转，唤醒跑高速旋转，完成态才跑呼吸与绕环圆点
    val slowSpin = if (!isDone && !isWaking) {
        val inf = rememberInfiniteTransition(label = "lockSlowInf")
        val deg by inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
            label = "slowSpin"
        )
        deg
    } else 0f

    val fastSpin = if (isWaking) {
        val inf = rememberInfiniteTransition(label = "lockFastInf")
        val deg by inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
            label = "fastSpin"
        )
        deg
    } else 0f

    val breathe = if (isDone) {
        val inf = rememberInfiniteTransition(label = "lockDoneInf")
        val sc by inf.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1350, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "doneBreathe"
        )
        sc
    } else 1f

    val pipPulse = if (isDone) {
        val inf = rememberInfiniteTransition(label = "lockPipInf")
        val p by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Restart),
            label = "pipPulse"
        )
        p
    } else 0f

    // 2. 唤醒进度弧 + 亮点绕行（若打开页面时已处于解锁/出水态，首帧直接落位）
    val ringProgress = remember { Animatable(if (isDone) 1f else 0f) }
    val shakeOffset = remember { Animatable(0f) }

    // 3. 锁体 → 水滴变形进度 (0=锁, 1=水滴)
    val morphProgress = remember { Animatable(if (isDone) 1f else 0f) }
    val shackleFly = remember { Animatable(if (isDone) 1f else 0f) }
    val jellyX = remember { Animatable(1f) }
    val jellyY = remember { Animatable(1f) }

    // 缓存复用的 Path，避免每帧在 Canvas 里反复分配对象
    val shacklePath = remember { Path() }
    val bodyPath = remember { Path() }
    val smilePath = remember { Path() }

    // 4. 水花爆开（两圈波纹 + 8 道短线）
    val burstProgress = remember { Animatable(0f) }
    val rayAngles = remember {
        FloatArray(8) { i -> i * 45f + (Random.nextFloat() * 16f - 8f) }
    }

    // 5. 水滴左右张望 + 眨眼
    var lookOffset by remember { mutableStateOf(0f) }
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        when (phase) {
            WaterPhase.LOCKED, WaterPhase.ENDING -> {
                ringProgress.animateTo(0f, tween(260))
                morphProgress.animateTo(0f, tween(340))
                shackleFly.animateTo(0f, tween(280))
                burstProgress.snapTo(0f)
                jellyX.snapTo(1f)
                jellyY.snapTo(1f)
            }
            WaterPhase.WAKING -> {
                burstProgress.snapTo(0f)
                launch {
                    ringProgress.snapTo(0f)
                    ringProgress.animateTo(0.92f, tween(840, easing = HotWaterMotion.SwipeEasing))
                }
                launch {
                    delay(80)
                    for (dx in listOf(-2.5f, 2.5f, -1.6f, 1.2f, 0f)) {
                        shakeOffset.animateTo(dx, tween(95))
                    }
                }
            }
            WaterPhase.UNLOCKED -> {
                ringProgress.snapTo(1f)
                if (morphProgress.value < 0.99f) {
                    launch {
                        morphProgress.animateTo(1f, tween(780, easing = HotWaterMotion.MorphEasing))
                    }
                    launch {
                        shackleFly.animateTo(1f, tween(560, easing = HotWaterMotion.SpringEasing))
                    }
                    launch {
                        burstProgress.snapTo(0f)
                        burstProgress.animateTo(1f, tween(920, easing = HotWaterMotion.OutFastEasing))
                    }
                    launch {
                        jellyX.animateTo(0.90f, tween(180))
                        jellyY.animateTo(1.13f, tween(180))
                        jellyX.animateTo(1.12f, tween(160))
                        jellyY.animateTo(0.91f, tween(160))
                        jellyX.animateTo(1f, tween(220))
                        jellyY.animateTo(1f, tween(220))
                    }
                } else {
                    shackleFly.snapTo(1f)
                }
            }
            WaterPhase.DISPENSING -> {
                ringProgress.snapTo(1f)
                morphProgress.snapTo(1f)
                shackleFly.snapTo(1f)
            }
        }
    }

    LaunchedEffect(isDone) {
        if (!isDone) {
            lookOffset = 0f
            blinking = false
            return@LaunchedEffect
        }
        launch {
            delay(1200)
            while (true) {
                lookOffset = -3f
                delay(700)
                lookOffset = 3f
                delay(700)
                lookOffset = 0f
                delay(2400)
            }
        }
        launch {
            while (true) {
                delay(2400)
                blinking = true
                delay(120)
                blinking = false
                delay(180)
                blinking = true
                delay(120)
                blinking = false
                delay(2200)
            }
        }
    }

    val animatedLook by animateFloatAsState(
        targetValue = lookOffset,
        animationSpec = tween(420, easing = EaseInOutSine),
        label = "lockLook"
    )
    val blinkScale by animateFloatAsState(
        targetValue = if (blinking) 0.12f else 1f,
        animationSpec = tween(90),
        label = "lockBlink"
    )
    val bodyColor by animateColorAsState(
        targetValue = when {
            isDone -> HotWaterColors.WaterBlue
            isWaking -> HotWaterColors.Primary
            else -> HotWaterColors.LockIdle
        },
        animationSpec = tween(500),
        label = "lockBodyColor"
    )
    val shackleColor by animateColorAsState(
        targetValue = if (isWaking || isDone) HotWaterColors.Primary else HotWaterColors.LockIdle,
        animationSpec = tween(420),
        label = "shackleColor"
    )
    val dotsAlpha by animateFloatAsState(
        targetValue = if (isDone) 0f else 1f,
        animationSpec = tween(420),
        label = "dotsAlpha"
    )

    Box(
        modifier = modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // 底层柔和光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HotWaterColors.Primary.copy(alpha = if (isDone) 0.18f else 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.56f
                ),
                radius = size.width * 0.56f,
                center = center
            )

            val ringRadius = 82.dp.toPx()

            // 外圈虚线点环
            if (dotsAlpha > 0.01f) {
                rotate(degrees = if (isWaking) fastSpin else slowSpin, pivot = center) {
                    drawCircle(
                        color = HotWaterColors.DotsIdle.copy(alpha = dotsAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(
                            width = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 13.dp.toPx()))
                        )
                    )
                }
            }

            // 实线进度圆环
            if (ringProgress.value > 0.01f) {
                drawArc(
                    color = HotWaterColors.Primary,
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress.value,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 5.4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 唤醒过程中的轨道亮点
                if (isWaking && ringProgress.value < 0.99f) {
                    val rad = (-90f + 360f * ringProgress.value) * (PI / 180.0)
                    val dotPos = Offset(
                        center.x + (ringRadius * cos(rad)).toFloat(),
                        center.y + (ringRadius * sin(rad)).toFloat()
                    )
                    drawCircle(HotWaterColors.Primary.copy(alpha = 0.18f), 10.dp.toPx(), dotPos)
                    drawCircle(HotWaterColors.Primary, 5.5.dp.toPx(), dotPos)
                }
            }

            // 已解锁态环上 3 颗呼吸圆点
            if (isDone) {
                val angles = listOf(-90.0, 30.0, 150.0)
                angles.forEachIndexed { idx, deg ->
                    val phaseShift = ((pipPulse + idx * 0.22f) % 1f)
                    val pulse = sin(phaseShift * PI).toFloat()
                    val rad = deg * PI / 180.0
                    val pos = Offset(
                        center.x + (ringRadius * cos(rad)).toFloat(),
                        center.y + (ringRadius * sin(rad)).toFloat()
                    )
                    drawCircle(
                        color = HotWaterColors.Primary.copy(alpha = 0.35f + pulse * 0.65f),
                        radius = (3.4f + pulse * 2.1f).dp.toPx(),
                        center = pos
                    )
                }
            }

            // 水花爆开：两圈扩散细环 + 8 道放射短线
            val bp = burstProgress.value
            if (bp > 0.01f && bp < 0.99f) {
                val w1Scale = 0.34f + bp * 1.28f
                val w1Alpha = (1f - bp) * 0.55f
                drawCircle(
                    color = HotWaterColors.Primary.copy(alpha = w1Alpha),
                    radius = 66.dp.toPx() * w1Scale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                if (bp > 0.14f) {
                    val bp2 = ((bp - 0.14f) / 0.86f).coerceIn(0f, 1f)
                    drawCircle(
                        color = HotWaterColors.WaterBlue.copy(alpha = (1f - bp2) * 0.5f),
                        radius = 66.dp.toPx() * (0.34f + bp2 * 1.28f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                rayAngles.forEachIndexed { i, deg ->
                    val localT = ((bp - i * 0.025f) / 0.78f).coerceIn(0f, 1f)
                    if (localT > 0f && localT < 1f) {
                        val rayAlpha = if (localT < 0.22f) localT / 0.22f else 1f - ((localT - 0.22f) / 0.78f)
                        val dist = (22f + localT * 52f).dp.toPx()
                        val len = (if (i % 2 == 0) 20f else 15f).dp.toPx() * (1f - localT * 0.55f)
                        val rad = (deg - 90f) * PI / 180.0
                        val start = Offset(
                            center.x + (dist * cos(rad)).toFloat(),
                            center.y + (dist * sin(rad)).toFloat()
                        )
                        val end = Offset(
                            center.x + ((dist + len) * cos(rad)).toFloat(),
                            center.y + ((dist + len) * sin(rad)).toFloat()
                        )
                        drawLine(
                            color = (if (i % 2 == 0) HotWaterColors.WaterBlue else HotWaterColors.PrimaryLite)
                                .copy(alpha = rayAlpha),
                            start = start,
                            end = end,
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 锁体 → 水滴（坐标系 76 x 96 居中映射）
            val lockW = 86.dp.toPx()
            val lockH = 104.dp.toPx()
            val left = center.x - lockW / 2f
            val top = center.y - lockH / 2f
            val sx = lockW / 76f
            val sy = lockH / 96f

            val doneScale = if (isDone) breathe else 1f
            val shakePx = shakeOffset.value.dp.toPx()
            withTransform({
                translate(left = shakePx, top = 0f)
                scale(
                    scaleX = jellyX.value * doneScale,
                    scaleY = jellyY.value * doneScale,
                    pivot = center
                )
            }) {
                // 锁环（解锁时向左上旋转弹飞淡出）
                val fly = shackleFly.value
                val shackleAlpha = (1f - ((fly - 0.45f) / 0.55f).coerceIn(0f, 1f))
                if (shackleAlpha > 0.01f) {
                    withTransform({
                        translate(
                            left = -27f * sx * fly,
                            top = -35f * sy * fly
                        )
                        rotate(
                            degrees = -74f * fly,
                            pivot = Offset(left + 22f * sx, top + 42f * sy)
                        )
                    }) {
                        shacklePath.reset()
                        shacklePath.apply {
                            moveTo(left + 22f * sx, top + 44f * sy)
                            lineTo(left + 22f * sx, top + 30f * sy)
                            cubicTo(
                                left + 22f * sx, top + 14f * sy,
                                left + 54f * sx, top + 14f * sy,
                                left + 54f * sx, top + 30f * sy
                            )
                            lineTo(left + 54f * sx, top + 44f * sy)
                        }
                        drawPath(
                            path = shacklePath,
                            color = shackleColor.copy(alpha = shackleAlpha),
                            style = Stroke(width = 7f * sx, cap = StrokeCap.Round)
                        )
                    }
                }

                // 4 段三次贝塞尔路径锚点线性插值：圆角锁体 ↔ 水滴
                val m = morphProgress.value
                bodyPath.reset()
                bodyPath.apply {
                    moveTo(left + 38f * sx, top + lerpF(42f, 18f, m) * sy)
                    cubicTo(
                        left + lerpF(62f, 47f, m) * sx, top + lerpF(42f, 46f, m) * sy,
                        left + 64f * sx, top + lerpF(44f, 54f, m) * sy,
                        left + 64f * sx, top + lerpF(63f, 68f, m) * sy
                    )
                    cubicTo(
                        left + 64f * sx, top + lerpF(82f, 81f, m) * sy,
                        left + lerpF(62f, 52f, m) * sx, top + lerpF(84f, 90f, m) * sy,
                        left + 38f * sx, top + lerpF(84f, 90f, m) * sy
                    )
                    cubicTo(
                        left + lerpF(14f, 24f, m) * sx, top + lerpF(84f, 90f, m) * sy,
                        left + 12f * sx, top + lerpF(82f, 81f, m) * sy,
                        left + 12f * sx, top + lerpF(63f, 68f, m) * sy
                    )
                    cubicTo(
                        left + 12f * sx, top + lerpF(44f, 54f, m) * sy,
                        left + lerpF(14f, 29f, m) * sx, top + lerpF(42f, 46f, m) * sy,
                        left + 38f * sx, top + lerpF(42f, 18f, m) * sy
                    )
                    close()
                }
                drawPath(bodyPath, color = bodyColor)

                // 表情（未解锁闭眼横线，唤醒/解锁睁眼+笑嘴）
                val faceColor = HotWaterColors.Bg
                val fx = animatedLook * sx
                if (!isWaking && !isDone) {
                    drawLine(
                        faceColor,
                        Offset(left + 25f * sx, top + 60f * sy),
                        Offset(left + 32f * sx, top + 60f * sy),
                        2.6f * sx,
                        StrokeCap.Round
                    )
                    drawLine(
                        faceColor,
                        Offset(left + 44f * sx, top + 60f * sy),
                        Offset(left + 51f * sx, top + 60f * sy),
                        2.6f * sx,
                        StrokeCap.Round
                    )
                    drawLine(
                        faceColor,
                        Offset(left + 33f * sx, top + 72f * sy),
                        Offset(left + 43f * sx, top + 72f * sy),
                        2.6f * sx,
                        StrokeCap.Round
                    )
                } else {
                    val rx = 3.2f * sx
                    val ry = 3.4f * sy * blinkScale
                    drawOval(
                        faceColor,
                        Offset(left + 28.5f * sx + fx - rx, top + 62f * sy - ry),
                        Size(rx * 2f, ry * 2f)
                    )
                    drawOval(
                        faceColor,
                        Offset(left + 47.5f * sx + fx - rx, top + 62f * sy - ry),
                        Size(rx * 2f, ry * 2f)
                    )
                    smilePath.reset()
                    smilePath.apply {
                        moveTo(left + 32f * sx + fx, top + 71f * sy)
                        quadraticTo(left + 38f * sx + fx, top + 78f * sy, left + 44f * sx + fx, top + 71f * sy)
                    }
                    drawPath(smilePath, faceColor, style = Stroke(2.6f * sx, cap = StrokeCap.Round))
                }
            }
        }
    }
}
