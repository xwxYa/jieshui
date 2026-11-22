package com.qiekj.hotwater.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun DeviceCardItem(
    device: Device,
    index: Int,
    rowHeight: Dp,
    isSwipedOpen: Boolean,
    isDragging: Boolean,
    isCrushing: Boolean,
    dragY: Float,
    onSwipeStateChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    onCrushFinished: () -> Unit,
    onDragStart: (Int) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val delWidthPx = with(density) { 88.dp.toPx() }
    val openThresholdPx = with(density) { 74.dp.toPx() }
    val latestIndex by rememberUpdatedState(index)
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDragDelta by rememberUpdatedState(onDragDelta)
    val latestDragEnd by rememberUpdatedState(onDragEnd)

    val entryAlpha = remember(device.goodsId) { Animatable(0f) }
    val entryY = remember(device.goodsId) { Animatable(12f) }
    LaunchedEffect(device.goodsId) {
        delay(index * 75L)
        launch { entryAlpha.animateTo(1f, tween(320)) }
        launch { entryY.animateTo(0f, tween(420, easing = HotWaterMotion.SpringEasing)) }
    }

    val swipeX = remember(device.goodsId) { Animatable(0f) }
    LaunchedEffect(isSwipedOpen) {
        val target = if (isSwipedOpen) -delWidthPx else 0f
        if (abs(swipeX.value - target) > 1f) {
            swipeX.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }

    // 仅在真正长按拖拽时才运行微抖动画，平时零空转开销
    val shiverRot = if (isDragging) {
        val shiverInf = rememberInfiniteTransition(label = "shiver")
        val angle by shiverInf.animateFloat(
            initialValue = -0.85f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(180, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "shiverAngle"
        )
        angle
    } else {
        0f
    }
    val liftShadow by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 3.dp,
        animationSpec = tween(180),
        label = "liftShadow"
    )

    val sx = max(0f, -swipeX.value)
    val revealRatio = (sx / delWidthPx).coerceIn(0f, 1f)
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (cardPressed && !isDragging && revealRatio < 0.05f) 0.976f else 1f,
        animationSpec = HotWaterMotion.bouncySpring(),
        label = "cardPressScale"
    )
    val k = min(sx / delWidthPx, 1.15f)
    val cardRubberX = 1f + k * 0.105f
    val cardRubberY = 1f - k * 0.042f
    val over = max(0f, sx - delWidthPx)
    val overT = min(over / with(density) { 50.dp.toPx() }, 1f)
    val delRubberX = 1f + overT * 0.58f
    val delRubberY = 1f - overT * 0.18f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (isDragging || isCrushing) 10f else 0f)
            .graphicsLayer {
                alpha = entryAlpha.value
                translationY = entryY.value.dp.toPx() + dragY
                rotationZ = if (isDragging) shiverRot else 0f
                scaleX = if (isDragging) 1.028f else 1f
                scaleY = if (isDragging) 1.028f else 1f
            }
            .pointerInput(device.goodsId, isCrushing) {
                if (isCrushing) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestDragStart(latestIndex) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestDragDelta(dragAmount.y)
                    },
                    onDragEnd = { latestDragEnd() },
                    onDragCancel = { latestDragEnd() }
                )
            }
    ) {
        if (!isCrushing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
            ) {
                if (revealRatio > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { (sx + 24.dp.toPx()).toDp() }.coerceAtLeast(88.dp))
                            .fillMaxHeight()
                            .background(HotWaterColors.Danger.copy(alpha = revealRatio))
                            .clickable(onClick = onRequestDelete),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .width(with(density) { max(delWidthPx, sx).toDp() })
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "删除",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = delRubberX
                                    scaleY = delRubberY
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(swipeX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .shadow(
                            elevation = liftShadow,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = Color(0x288F4C38),
                            spotColor = Color(0x208F4C38)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(HotWaterColors.Surface)
                        .pointerInput(device.goodsId, isDragging) {
                            if (isDragging) return@pointerInput
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val raw = swipeX.value + dragAmount
                                        val damped = when {
                                            raw > 0f -> raw * 0.16f
                                            raw < -delWidthPx - 40f -> -delWidthPx - 40f + (raw + delWidthPx + 40f) * 0.22f
                                            else -> raw
                                        }
                                        swipeX.snapTo(damped)
                                    }
                                },
                                onDragEnd = {
                                    val open = swipeX.value < -openThresholdPx
                                    onSwipeStateChange(open)
                                    scope.launch {
                                        swipeX.animateTo(
                                            if (open) -delWidthPx else 0f,
                                            spring(dampingRatio = 0.72f, stiffness = 420f)
                                        )
                                    }
                                },
                                onDragCancel = {
                                    onSwipeStateChange(false)
                                    scope.launch {
                                        swipeX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
                                    }
                                }
                            )
                        }
                        .clickable(
                            interactionSource = cardInteraction,
                            indication = null,
                            onClick = onClick
                        )
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                scaleX = cardRubberX
                                scaleY = cardRubberY
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        StatusGlowDot(online = device.online, workStatus = device.workStatus)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name.ifBlank { "未命名设备" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HotWaterColors.OnSurface,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = device.subtitle(),
                                fontSize = 12.sp,
                                color = HotWaterColors.OnSurfaceVar,
                                maxLines = 1
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFC9B8B2),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            DustCrushOverlay(
                cardRightRatio = 0.74f,
                onFinished = onCrushFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            )
        }
    }
}

@Composable
internal fun StatusGlowDot(online: Boolean, workStatus: Int) {
    val core = when {
        !online -> HotWaterColors.OfflineGray
        workStatus == 2 -> HotWaterColors.WaterBlue
        workStatus == 3 -> Color(0xFFE89B48)
        workStatus == 1 -> HotWaterColors.OnlineGreen
        else -> Color(0xFF9EC5AD)
    }
    val pulseScale = if (online && workStatus == 2) {
        val inf = rememberInfiniteTransition(label = "runningDot")
        val s by inf.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "dotPulse"
        )
        s
    } else {
        1f
    }

    Box(
        modifier = Modifier.size(17.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .background(core.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(core)
        )
    }
}
