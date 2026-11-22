package com.qiekj.hotwater.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.hardware.scanner.CameraScanController
import com.qiekj.hotwater.ui.ScanLookupState
import com.qiekj.hotwater.ui.components.MorphButtonState
import com.qiekj.hotwater.ui.components.MorphingActionButton
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterModalOverlay
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(
    scanState: ScanLookupState,
    scanMessage: String,
    pendingDevice: Device?,
    onDecoded: (String) -> Unit,
    onManualSubmit: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    onRescan: () -> Unit,
    onBuzz: (Int, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchOn by remember { mutableStateOf(false) }
    var bindTick by remember { mutableIntStateOf(0) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var lastManualFocusMs by remember { mutableStateOf(0L) }
    var tapFocusOffset by remember { mutableStateOf<Offset?>(null) }
    val tapFocusRing = remember { Animatable(0f) }

    val controller = remember { CameraScanController(onDecoded = onDecoded) }

    // 定时自动续一次中心对焦与中心测光，避免静止举着手机时焦点停在无穷远
    LaunchedEffect(bindTick, scanState, showManualDialog) {
        if (!controller.isBound || scanState != ScanLookupState.IDLE || showManualDialog) return@LaunchedEffect
        delay(220)
        controller.focusCenter()
        while (scanState == ScanLookupState.IDLE && !showManualDialog && controller.isBound) {
            delay(2800)
            if (System.currentTimeMillis() - lastManualFocusMs >= 2600L) {
                controller.focusCenter()
            }
        }
    }

    // 点按手动对焦小圆环动画
    LaunchedEffect(tapFocusOffset) {
        if (tapFocusOffset != null) {
            tapFocusRing.snapTo(0f)
            tapFocusRing.animateTo(1f, tween(460, easing = HotWaterMotion.OutFastEasing))
            tapFocusOffset = null
        }
    }

    LaunchedEffect(scanState, showManualDialog) {
        controller.setPaused((scanState != ScanLookupState.IDLE) || showManualDialog)
        if (scanState == ScanLookupState.FOUND) {
            torchOn = false
            controller.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }

    BackHandler {
        if (showManualDialog) showManualDialog = false else onBack()
    }

    val sweepInf = rememberInfiniteTransition(label = "scanSweep")
    val sweepProgress by sweepInf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sweepY"
    )

    val lockProgress = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val cornerColor by animateColorAsState(
        targetValue = if (scanState == ScanLookupState.FOUND) HotWaterColors.OnlineGreen else HotWaterColors.Primary,
        animationSpec = tween(220),
        label = "scanCornerColor"
    )
    LaunchedEffect(scanState) {
        if (scanState == ScanLookupState.FOUND) {
            onBuzz(18, 160)
            flashAlpha.snapTo(0.26f)
            coroutineScope {
                launch { lockProgress.animateTo(1f, tween(240, easing = HotWaterMotion.SpringEasing)) }
                launch { flashAlpha.animateTo(0f, tween(320)) }
            }
        } else {
            flashAlpha.snapTo(0f)
            lockProgress.animateTo(0f, tween(200))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = HotWaterColors.OnSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "扫码加设备",
                    fontSize = 19.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HotWaterColors.OnSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                if (hasCamPermission && scanState != ScanLookupState.FOUND) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { torchOn = controller.toggleTorch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "开关照明",
                            tint = if (torchOn) HotWaterColors.Primary else HotWaterColors.Muted,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(226.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF12100F))
                        .pointerInput(hasCamPermission, scanState) {
                            if (!hasCamPermission || scanState != ScanLookupState.IDLE) return@pointerInput
                            detectTapGestures { offset ->
                                lastManualFocusMs = System.currentTimeMillis()
                                tapFocusOffset = offset
                                controller.focusAt(offset.x, offset.y)
                            }
                        }
                ) {
                    if (hasCamPermission && scanState != ScanLookupState.FOUND) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }.also { pv ->
                                    controller.bind(ctx, lifecycleOwner, pv) {
                                        bindTick++
                                    }
                                }
                            }
                        )
                    } else if (!hasCamPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { permLauncher.launch(Manifest.permission.CAMERA) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "未授予相机权限",
                                color = Color(0xFFD6C0BA),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "点此重新授权",
                                color = HotWaterColors.PrimaryLite,
                                fontSize = 12.sp,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val inset = (16f + lockProgress.value * 16f).dp.toPx()
                        val cornerLen = (24f - lockProgress.value * 3f).dp.toPx()
                        val sw = 3.dp.toPx()
                        val col = cornerColor

                        if (flashAlpha.value > 0.005f) {
                            drawRoundRect(
                                color = HotWaterColors.OnlineGreen.copy(alpha = flashAlpha.value),
                                cornerRadius = CornerRadius(24.dp.toPx())
                            )
                        }

                        drawLine(col, Offset(inset, inset), Offset(inset + cornerLen, inset), sw, StrokeCap.Round)
                        drawLine(col, Offset(inset, inset), Offset(inset, inset + cornerLen), sw, StrokeCap.Round)
                        drawLine(col, Offset(size.width - inset, inset), Offset(size.width - inset - cornerLen, inset), sw, StrokeCap.Round)
                        drawLine(col, Offset(size.width - inset, inset), Offset(size.width - inset, inset + cornerLen), sw, StrokeCap.Round)
                        drawLine(col, Offset(inset, size.height - inset), Offset(inset + cornerLen, size.height - inset), sw, StrokeCap.Round)
                        drawLine(col, Offset(inset, size.height - inset), Offset(inset, size.height - inset - cornerLen), sw, StrokeCap.Round)
                        drawLine(col, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - cornerLen, size.height - inset), sw, StrokeCap.Round)
                        drawLine(col, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - cornerLen), sw, StrokeCap.Round)

                        if (scanState == ScanLookupState.IDLE || scanState == ScanLookupState.SEARCHING) {
                            val lineY = inset + 10.dp.toPx() + (size.height - (inset + 10.dp.toPx()) * 2f) * sweepProgress
                            drawRoundRect(
                                color = HotWaterColors.Primary.copy(alpha = 0.88f),
                                topLeft = Offset(inset + 8.dp.toPx(), lineY),
                                size = Size(size.width - (inset + 8.dp.toPx()) * 2f, 2.2.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        val focusPos = tapFocusOffset
                        if (focusPos != null) {
                            val p = tapFocusRing.value
                            val radius = (26f - p * 10f).dp.toPx()
                            val alpha = (1f - p).coerceIn(0f, 1f)
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.9f),
                                radius = radius,
                                center = focusPos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = scanMessage,
                    fontSize = 12.5.sp,
                    color = HotWaterColors.Muted
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "扫不出来？手动输设备号",
                        fontSize = 12.5.sp,
                        color = HotWaterColors.Muted,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { showManualDialog = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    if (scanState == ScanLookupState.FOUND || scanState == ScanLookupState.ERROR) {
                        Text(
                            text = "重新扫码",
                            fontSize = 12.5.sp,
                            color = HotWaterColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable(onClick = onRescan)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = scanState == ScanLookupState.FOUND && pendingDevice != null,
                enter = fadeIn(tween(260)) + slideInVertically(HotWaterMotion.bouncySpring()) { it / 2 },
                exit = fadeOut()
            ) {
                val dev = pendingDevice
                if (dev != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(5.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x228F4C38))
                                .clip(RoundedCornerShape(16.dp))
                                .background(HotWaterColors.Surface)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (dev.online) HotWaterColors.OnlineGreen else HotWaterColors.OfflineGray)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name.ifBlank { "这台机器" },
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HotWaterColors.OnSurface
                                )
                                Text(
                                    text = dev.subtitle(),
                                    fontSize = 11.5.sp,
                                    color = HotWaterColors.OnSurfaceVar
                                )
                            }
                        }

                        MorphingActionButton(
                            text = "添加这台设备",
                            state = MorphButtonState.IDLE,
                            onClick = onConfirmAdd
                        )
                    }
                }
            }
        }

        HotWaterModalOverlay(
            visible = showManualDialog,
            onDismiss = { showManualDialog = false }
        ) {
            Text(
                text = "手动输设备号",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.OnSurface
            )
            Text(
                text = "机器贴纸上那串数字，一般是 15 位",
                fontSize = 12.sp,
                color = HotWaterColors.Muted
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(HotWaterColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (manualInput.isEmpty()) {
                    Text(
                        text = "输入机器上的 15 位编号",
                        fontSize = 14.5.sp,
                        color = HotWaterColors.Hint
                    )
                }
                BasicTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it.trim() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HotWaterColors.OnSurface
                    ),
                    cursorBrush = SolidColor(HotWaterColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MorphingActionButton(
                text = "就用这台",
                state = MorphButtonState.IDLE,
                onClick = {
                    showManualDialog = false
                    onManualSubmit(manualInput)
                }
            )
            MorphingActionButton(
                text = "算了",
                state = MorphButtonState.IDLE,
                containerColor = HotWaterColors.Dark,
                onClick = { showManualDialog = false }
            )
        }
    }
}
