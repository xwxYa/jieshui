package com.qiekj.hotwater

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.qiekj.hotwater.ui.theme.HotWaterMotion
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
                        // 主壳层常驻底层不被销毁：进入/退出二级页零列表重建、零卡片入场重播、零滚动位置丢失
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
                                            onOpenScan = vm.scan::open,
                                            onOpenWater = vm.water::open,
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
                                            onOpenSummary = vm::openSummary
                                        )
                                    }
                                }
                            }
                            SpringTabBar(
                                selectedTab = shell.mainTab,
                                onSelect = vm::selectTab
                            )
                        }

                        // 二级页面纯 GPU 图层进出（无逐帧共享边界重测量），保持 60/120fps 顺滑
                        AnimatedContent(
                            targetState = overlayRoute,
                            transitionSpec = {
                                if (targetState != OverlayRoute.None) {
                                    (fadeIn(tween(220, easing = HotWaterMotion.OutFastEasing)) +
                                        slideInHorizontally(tween(240, easing = HotWaterMotion.OutFastEasing)) { it / 10 } +
                                        scaleIn(tween(240, easing = HotWaterMotion.OutFastEasing), initialScale = 0.965f)) togetherWith
                                        fadeOut(tween(140))
                                } else {
                                    fadeIn(tween(140)) togetherWith
                                        (fadeOut(tween(190, easing = HotWaterMotion.OutFastEasing)) +
                                            slideOutHorizontally(tween(210, easing = HotWaterMotion.OutFastEasing)) { it / 12 } +
                                            scaleOut(tween(210, easing = HotWaterMotion.OutFastEasing), targetScale = 0.965f))
                                }
                            },
                            label = "mainOverlay"
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
