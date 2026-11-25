package com.qiekj.hotwater

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.ui.AppScreen
import com.qiekj.hotwater.ui.HotWaterViewModel
import com.qiekj.hotwater.ui.MainTab
import com.qiekj.hotwater.ui.components.SpringTabBar
import com.qiekj.hotwater.ui.devices.DeviceListScreen
import com.qiekj.hotwater.ui.login.LoginScreen
import com.qiekj.hotwater.ui.orders.OrderScreen
import com.qiekj.hotwater.ui.orders.SummaryScreen
import com.qiekj.hotwater.ui.scan.ScanScreen
import com.qiekj.hotwater.ui.theme.HotWaterTheme
import com.qiekj.hotwater.ui.theme.HotWaterToastOverlay
import com.qiekj.hotwater.ui.water.WaterScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val app = application as HotWaterApp
        setContent {
            HotWaterTheme {
                val vm: HotWaterViewModel = viewModel(factory = HotWaterViewModel.factory(app))
                HotWaterRoot(vm = vm)
            }
        }
    }
}

private sealed interface OverlayRoute {
    data object None : OverlayRoute
    data class Water(val device: Device) : OverlayRoute
    data object Scan : OverlayRoute
    data object Summary : OverlayRoute
}

@Composable
private fun HotWaterRoot(vm: HotWaterViewModel) {
    val shell by vm.shell.collectAsStateWithLifecycle()
    val auth by vm.auth.state.collectAsStateWithLifecycle()
    val devices by vm.devices.state.collectAsStateWithLifecycle()
    val orders by vm.orders.state.collectAsStateWithLifecycle()
    val scan by vm.scan.state.collectAsStateWithLifecycle()
    val water by vm.water.state.collectAsStateWithLifecycle()

    var originRect by remember { mutableStateOf<Rect?>(null) }
    var originCornerDp by remember { mutableStateOf(18f) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = shell.screen,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "rootScreen"
        ) { screen ->
            when (screen) {
                AppScreen.LOGIN -> {
                    LoginScreen(
                        auth = auth,
                        onPhoneChange = vm.auth::onPhoneChange,
                        onCodeChange = vm.auth::onCodeChange,
                        onSendCode = vm.auth::sendSmsCode,
                        onSubmitLogin = vm.auth::submitLogin,
                        onEnterDemo = vm::enterDemoMode,
                        onBuzz = vm::buzz
                    )
                }

                AppScreen.MAIN -> {
                    val overlayRoute = when {
                        scan.scanOpen -> OverlayRoute.Scan
                        water.device != null -> OverlayRoute.Water(water.device!!)
                        shell.summaryOpen -> OverlayRoute.Summary
                        else -> OverlayRoute.None
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = shell.mainTab,
                                    transitionSpec = {
                                        val toRight = targetState == MainTab.ORDERS
                                        (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { w -> if (toRight) w / 16 else -w / 16 }) togetherWith
                                            (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { w -> if (toRight) -w / 16 else w / 16 })
                                    },
                                    label = "mainTabCrossfade"
                                ) { tab ->
                                    when (tab) {
                                        MainTab.DEVICES -> DeviceListScreen(
                                            devicesState = devices,
                                            demoMode = shell.demoMode,
                                            loggedInPhone = auth.loggedInPhone,
                                            onRefreshDevices = { vm.devices.refresh(userInitiated = true) },
                                            onOpenScan = { rect ->
                                                originRect = rect
                                                originCornerDp = 28f
                                                vm.scan.open()
                                            },
                                            onOpenWater = { device, rect ->
                                                originRect = rect
                                                originCornerDp = 18f
                                                vm.water.open(device)
                                            },
                                            onReorder = vm.devices::reorder,
                                            onPersistRemoveDevice = vm.devices::persistRemove,
                                            onFinalizeRemoveDevice = vm.devices::finalizeRemove,
                                            onLogout = vm::logout,
                                            onBuzz = vm::buzz
                                        )

                                        MainTab.ORDERS -> OrderScreen(
                                            ordersState = orders,
                                            onRefreshOrders = { vm.orders.refresh(force = true, userInitiated = true) },
                                            onSelectYear = vm.orders::selectYear,
                                            onToggleMonth = vm.orders::toggleMonth,
                                            onOpenSummary = { rect ->
                                                originRect = rect
                                                originCornerDp = 18f
                                                vm.openSummary()
                                            }
                                        )
                                    }
                                }
                            }
                            SpringTabBar(
                                selectedTab = shell.mainTab,
                                onSelect = vm::selectTab
                            )
                        }

                        OriginMorphOverlay(
                            targetRoute = overlayRoute,
                            originRect = originRect,
                            originCornerDp = originCornerDp
                        ) { route ->
                            when (route) {
                                OverlayRoute.None -> Unit

                                is OverlayRoute.Water -> {
                                    WaterScreen(
                                        device = route.device,
                                        session = water,
                                        onBack = vm.water::close,
                                        onUnlock = vm.water::unlock,
                                        onEndWater = vm.water::endWater,
                                        onBleFallback = vm.water::triggerBleFallback
                                    )
                                }

                                OverlayRoute.Scan -> {
                                    ScanScreen(
                                        scanState = scan.scanState,
                                        scanMessage = scan.scanMessage,
                                        pendingDevice = scan.pendingScanDevice,
                                        onDecoded = vm.scan::lookupFromQr,
                                        onManualSubmit = vm.scan::lookupByManualNo,
                                        onConfirmAdd = vm.scan::confirmAdd,
                                        onRescan = vm.scan::resetToIdle,
                                        onShowToast = vm::showToast,
                                        onBuzz = vm::buzz,
                                        onBack = vm.scan::close
                                    )
                                }

                                OverlayRoute.Summary -> {
                                    SummaryScreen(
                                        stats = orders.orderStats,
                                        onBack = vm::closeSummary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HotWaterToastOverlay(
            message = shell.toast?.message,
            toastId = shell.toast?.id,
            onDismiss = vm::clearToast
        )
    }
}

/**
 * 纯 GPU 矩阵变换原地展开/收回覆盖层：
 * - 从被点击卡片或右上角「+」的屏幕矩形原地放大铺满全屏，返回时原路缩回原位；
 * - 动画全程仅更新 graphicsLayer，不触发 Compose 重测量与底层列表重建。
 */
@Composable
private fun OriginMorphOverlay(
    targetRoute: OverlayRoute,
    originRect: Rect?,
    originCornerDp: Float,
    content: @Composable (OverlayRoute) -> Unit
) {
    var activeRoute by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(targetRoute) {
        if (targetRoute != OverlayRoute.None) {
            val freshOpen = activeRoute == OverlayRoute.None
            activeRoute = targetRoute
            if (freshOpen) {
                progress.snapTo(0f)
            }
            progress.animateTo(1f, tween(270, easing = FastOutSlowInEasing))
        } else if (activeRoute != OverlayRoute.None) {
            progress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            activeRoute = OverlayRoute.None
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        if (activeRoute != OverlayRoute.None) {
            val p = progress.value
            val rootW = containerSize.width.toFloat().coerceAtLeast(1f)
            val rootH = containerSize.height.toFloat().coerceAtLeast(1f)
            val rect = originRect

            val startScale = if (rect != null && rect.width > 0f) {
                (rect.width / rootW).coerceIn(0.16f, 0.86f)
            } else {
                0.88f
            }
            val scale = startScale + (1f - startScale) * p
            val dx = if (rect != null) (rect.center.x - rootW / 2f) * (1f - p) else 0f
            val dy = if (rect != null) (rect.center.y - rootH / 2f) * (1f - p) else 0f
            val layerAlpha = (p / 0.32f).coerceIn(0f, 1f)
            val corner = ((1f - p) * originCornerDp).dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = 0.14f * p))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = dx
                        translationY = dy
                        alpha = layerAlpha
                        shape = RoundedCornerShape(corner)
                        clip = p < 0.995f
                    }
            ) {
                content(activeRoute)
            }
        }
    }
}
