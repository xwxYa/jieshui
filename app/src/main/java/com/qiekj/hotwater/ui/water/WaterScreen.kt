package com.qiekj.hotwater.ui.water

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.ui.WaterPhase
import com.qiekj.hotwater.ui.WaterSessionUiState
import com.qiekj.hotwater.ui.components.MorphButtonState
import com.qiekj.hotwater.ui.components.MorphingActionButton
import com.qiekj.hotwater.ui.theme.HotWaterColors

@Composable
fun WaterScreen(
    device: Device,
    session: WaterSessionUiState,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onEndWater: () -> Unit,
    onBleFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dev = session.device ?: device
    val context = LocalContext.current

    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val blePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onBleFallback()
        }
    }

    fun requestBleAndRun() {
        val granted = blePermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onBleFallback() else blePermLauncher.launch(blePermissions)
    }

    BackHandler(onBack = onBack)

    val isLit = session.phase == WaterPhase.UNLOCKED || session.phase == WaterPhase.DISPENSING
    val bigTitle = when (session.phase) {
        WaterPhase.LOCKED -> "设备已锁定"
        WaterPhase.WAKING -> "正在唤醒…"
        WaterPhase.UNLOCKED -> "已解锁"
        WaterPhase.DISPENSING -> "正在出水"
        WaterPhase.ENDING -> "正在收尾…"
    }
    val titleColor by animateColorAsState(
        targetValue = if (isLit) HotWaterColors.Primary else HotWaterColors.OnSurface,
        animationSpec = tween(360),
        label = "waterTitleColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶栏
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
                text = "接水",
                fontSize = 19.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HotWaterColors.OnSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            MorphingLockStage(phase = session.phase)
        }

        Spacer(Modifier.height(10.dp))

        // 状态标题 + 实时轮询文案
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = bigTitle,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { -it / 2 })
                },
                label = "waterBigTitle"
            ) { text ->
                Text(
                    text = text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = session.syncStatusText,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = HotWaterColors.OnSurfaceVar,
                textAlign = TextAlign.Center
            )
        }

        // 设备名称与单价
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HotWaterColors.OutlineVar)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dev.name.ifBlank { "未命名设备" },
                    fontSize = 13.sp,
                    color = HotWaterColors.OnSurfaceVar
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = dev.price.ifBlank { "—" },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HotWaterColors.OnSurface
                    )
                    if (dev.price.isNotBlank()) {
                        Text(
                            text = " 元/100mL",
                            fontSize = 11.sp,
                            color = Color(0xFF9C8A84)
                        )
                    }
                }
            }
        }

        // BLE 近场兜底入口
        AnimatedVisibility(
            visible = session.showBleFallback && session.phase != WaterPhase.DISPENSING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HotWaterColors.PillTint)
                        .clickable(enabled = !session.bleBusy, onClick = ::requestBleAndRun)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = HotWaterColors.Primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (session.bleBusy) "蓝牙直连中…" else "信号差？走蓝牙近场解锁",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HotWaterColors.Primary
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部双胶囊按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val unlockBtnState = when (session.phase) {
                WaterPhase.LOCKED -> MorphButtonState.IDLE
                WaterPhase.WAKING -> MorphButtonState.LOADING
                WaterPhase.UNLOCKED, WaterPhase.DISPENSING -> MorphButtonState.SUCCESS
                WaterPhase.ENDING -> MorphButtonState.DISABLED
            }
            MorphingActionButton(
                text = "解锁设备",
                loadingText = "正在解锁…",
                successText = if (session.phase == WaterPhase.DISPENSING) "出水中" else "已解锁",
                state = unlockBtnState,
                onClick = onUnlock
            )

            val endBtnState = when (session.phase) {
                WaterPhase.UNLOCKED, WaterPhase.DISPENSING -> MorphButtonState.IDLE
                WaterPhase.ENDING -> MorphButtonState.LOADING
                else -> MorphButtonState.DISABLED
            }
            MorphingActionButton(
                text = "结束接水",
                loadingText = "收尾中…",
                state = endBtnState,
                containerColor = HotWaterColors.Dark,
                onClick = onEndWater
            )
        }
    }
}
