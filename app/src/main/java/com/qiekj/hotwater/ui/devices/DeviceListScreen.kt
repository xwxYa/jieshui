package com.qiekj.hotwater.ui.devices

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.ui.DevicesUiState
import com.qiekj.hotwater.ui.components.MorphButtonState
import com.qiekj.hotwater.ui.components.MorphingActionButton
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterModalOverlay
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    devicesState: DevicesUiState,
    demoMode: Boolean,
    loggedInPhone: String,
    onRefreshDevices: () -> Unit,
    onOpenScan: (Rect?) -> Unit,
    onOpenWater: (Device, Rect?) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onPersistRemoveDevice: (String) -> Unit,
    onFinalizeRemoveDevice: (String) -> Unit,
    onLogout: () -> Unit,
    onBuzz: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showAccountSheet by remember { mutableStateOf(false) }
    var swipedGoodsId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteDevice by remember { mutableStateOf<Device?>(null) }
    var crushingGoodsIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapsedGoodsIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var scanBtnBounds by remember { mutableStateOf<Rect?>(null) }
    val latestDeviceCount by rememberUpdatedState(devicesState.devices.size)

    val chevRotation by animateFloatAsState(
        targetValue = if (showAccountSheet) 180f else 0f,
        animationSpec = HotWaterMotion.bouncySpring(),
        label = "acctChev"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = swipedGoodsId != null
            ) {
                swipedGoodsId = null
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            swipedGoodsId = null
                            showAccountSheet = true
                        },
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "设备",
                        fontSize = 19.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HotWaterColors.OnSurface,
                        letterSpacing = (-0.4).sp
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HotWaterColors.PillTint)
                            .padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (demoMode) "演示模式" else loggedInPhone.ifBlank { "未登录" },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HotWaterColors.Primary
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = HotWaterColors.Primary,
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer { rotationZ = chevRotation }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .onGloballyPositioned { coords ->
                            scanBtnBounds = coords.boundsInRoot()
                        }
                        .clip(CircleShape)
                        .clickable {
                            swipedGoodsId = null
                            onOpenScan(scanBtnBounds)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "扫码添加设备",
                        tint = HotWaterColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = devicesState.devicesRefreshing,
                onRefresh = onRefreshDevices,
                modifier = Modifier.weight(1f)
            ) {
                if (devicesState.devices.isEmpty()) {
                    EmptyDevicesCard(onScan = { onOpenScan(null) })
                } else {
                    val density = LocalDensity.current
                    val itemHeightPx = with(density) { 80.dp.toPx() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = devicesState.devices,
                            key = { _, d -> d.goodsId }
                        ) { index, device ->
                            val isDragging = draggingIndex == index
                            val isCrushing = device.goodsId in crushingGoodsIds
                            val isCollapsed = device.goodsId in collapsedGoodsIds

                            val shiftTarget = if (draggingIndex >= 0 && !isDragging) {
                                val shiftCount = (dragOffsetY / itemHeightPx).roundToInt()
                                    .coerceIn(-draggingIndex, devicesState.devices.lastIndex - draggingIndex)
                                when {
                                    shiftCount > 0 && index in (draggingIndex + 1)..(draggingIndex + shiftCount) -> -itemHeightPx
                                    shiftCount < 0 && index in (draggingIndex + shiftCount)..<draggingIndex -> itemHeightPx
                                    else -> 0f
                                }
                            } else 0f
                            val animatedShift by animateFloatAsState(
                                targetValue = shiftTarget,
                                animationSpec = HotWaterMotion.crispSpring(),
                                label = "dragShift_${device.goodsId}"
                            )

                            val rowHeight by animateDpAsState(
                                targetValue = if (isCollapsed) 0.dp else 70.dp,
                                animationSpec = tween(260, easing = HotWaterMotion.SwipeEasing),
                                label = "rowCollapse_${device.goodsId}"
                            )

                            DeviceCardItem(
                                device = device,
                                index = index,
                                rowHeight = rowHeight,
                                isSwipedOpen = swipedGoodsId == device.goodsId,
                                isDragging = isDragging,
                                isCrushing = isCrushing,
                                dragY = if (isDragging) dragOffsetY else animatedShift,
                                onSwipeStateChange = { open ->
                                    swipedGoodsId = if (open) device.goodsId else if (swipedGoodsId == device.goodsId) null else swipedGoodsId
                                },
                                onClick = { bounds ->
                                    if (swipedGoodsId != null) {
                                        swipedGoodsId = null
                                    } else {
                                        onOpenWater(device, bounds)
                                    }
                                },
                                onRequestDelete = {
                                    pendingDeleteDevice = device
                                },
                                onCrushFinished = {
                                    crushingGoodsIds = crushingGoodsIds - device.goodsId
                                    collapsedGoodsIds = collapsedGoodsIds - device.goodsId
                                    onFinalizeRemoveDevice(device.goodsId)
                                },
                                onDragStart = { currentIdx ->
                                    swipedGoodsId = null
                                    onBuzz(22, 120)
                                    draggingIndex = currentIdx
                                    dragOffsetY = 0f
                                },
                                onDragDelta = { dy ->
                                    dragOffsetY += dy
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val count = latestDeviceCount
                                    if (from >= 0 && count > 0) {
                                        val delta = (dragOffsetY / itemHeightPx).roundToInt()
                                        val to = (from + delta).coerceIn(0, count - 1)
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                        if (to != from) onReorder(from, to)
                                    } else {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        HotWaterModalOverlay(
            visible = showAccountSheet,
            onDismiss = { showAccountSheet = false }
        ) {
            Text(
                text = "当前账号",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.OnSurface
            )
            Text(
                text = if (demoMode) "演示模式 · 不是真数据" else loggedInPhone.ifBlank { "未登录" },
                fontSize = 12.5.sp,
                color = HotWaterColors.Muted
            )
            Spacer(Modifier.height(4.dp))
            MorphingActionButton(
                text = if (demoMode) "退出演示模式" else "退出登录",
                state = MorphButtonState.IDLE,
                onClick = {
                    showAccountSheet = false
                    onLogout()
                }
            )
            MorphingActionButton(
                text = "算了",
                state = MorphButtonState.IDLE,
                containerColor = HotWaterColors.Dark,
                onClick = { showAccountSheet = false }
            )
        }

        HotWaterModalOverlay(
            visible = pendingDeleteDevice != null,
            onDismiss = { pendingDeleteDevice = null }
        ) {
            val target = pendingDeleteDevice
            Text(
                text = "删掉这台设备？",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.OnSurface
            )
            Text(
                text = target?.name?.ifBlank { "这台设备" } ?: "这台设备",
                fontSize = 12.5.sp,
                color = HotWaterColors.Muted
            )
            Spacer(Modifier.height(4.dp))
            MorphingActionButton(
                text = "删掉它",
                state = MorphButtonState.IDLE,
                containerColor = HotWaterColors.Danger,
                onClick = {
                    val dev = target ?: return@MorphingActionButton
                    pendingDeleteDevice = null
                    swipedGoodsId = null
                    onBuzz(26, 150)
                    onPersistRemoveDevice(dev.goodsId)
                    crushingGoodsIds = crushingGoodsIds + dev.goodsId
                    scope.launch {
                        delay(260)
                        collapsedGoodsIds = collapsedGoodsIds + dev.goodsId
                    }
                }
            )
            MorphingActionButton(
                text = "算了",
                state = MorphButtonState.IDLE,
                containerColor = HotWaterColors.Dark,
                onClick = { pendingDeleteDevice = null }
            )
        }
    }
}

@Composable
private fun EmptyDevicesCard(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(HotWaterColors.Surface)
            .padding(horizontal = 20.dp, vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(width = 34.dp, height = 45.dp)) {
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
            drawPath(p, Color(0xFFEADCD8))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "还没有设备",
            fontSize = 13.sp,
            color = HotWaterColors.Muted
        )
        Spacer(Modifier.height(16.dp))
        MorphingActionButton(
            text = "扫一扫",
            state = MorphButtonState.IDLE,
            onClick = onScan,
            modifier = Modifier.width(156.dp),
            height = 46.dp
        )
    }
}
