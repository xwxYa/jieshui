package com.qiekj.hotwater.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qiekj.hotwater.HotWaterApp
import com.qiekj.hotwater.data.local.LocalStore
import com.qiekj.hotwater.data.local.OrdersCache
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.hardware.Haptics
import com.qiekj.hotwater.hardware.ble.QiekjBleClient
import com.qiekj.hotwater.ui.state.AuthCoordinator
import com.qiekj.hotwater.ui.state.DevicesCoordinator
import com.qiekj.hotwater.ui.state.OrdersCoordinator
import com.qiekj.hotwater.ui.state.ScanCoordinator
import com.qiekj.hotwater.ui.state.WaterSessionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 极薄应用根门面：组合 5 个独立领域协调器，只管理顶层导航壳、演示模式与全局 Toast。
 */
class HotWaterViewModel(
    private val localStore: LocalStore,
    ordersCache: OrdersCache,
    api: QiekjApi,
    private val haptics: Haptics,
    bleClient: QiekjBleClient
) : ViewModel() {

    private val hasInitialToken = localStore.token.isNotBlank()
    private var toastCounter = 0L

    private val _shell = MutableStateFlow(
        AppShellState(
            screen = if (hasInitialToken) AppScreen.MAIN else AppScreen.LOGIN
        )
    )
    val shell: StateFlow<AppShellState> = _shell.asStateFlow()

    val auth = AuthCoordinator(
        scope = viewModelScope,
        localStore = localStore,
        api = api,
        haptics = haptics,
        isDemoMode = { _shell.value.demoMode },
        onAuthenticated = { enterMainReal() },
        onTokenExpired = { handleTokenExpired() }
    )

    val devices = DevicesCoordinator(
        scope = viewModelScope,
        localStore = localStore,
        api = api,
        isDemoMode = { _shell.value.demoMode },
        showToast = ::showToast,
        initialDevices = if (hasInitialToken) localStore.loadDevices() else emptyList(),
        initialLoaded = hasInitialToken
    )

    val orders = OrdersCoordinator(
        scope = viewModelScope,
        ordersCache = ordersCache,
        api = api,
        isDemoMode = { _shell.value.demoMode },
        showToast = ::showToast
    )

    val scan = ScanCoordinator(
        scope = viewModelScope,
        api = api,
        isDemoMode = { _shell.value.demoMode },
        showToast = ::showToast,
        onDeviceConfirmed = { dev -> devices.addDeviceToTop(dev) }
    )

    val water = WaterSessionCoordinator(
        scope = viewModelScope,
        api = api,
        haptics = haptics,
        bleClient = bleClient,
        isDemoMode = { _shell.value.demoMode },
        showToast = ::showToast,
        onTokenExpired = { handleTokenExpired() },
        onDeviceWorkStatusChanged = { goodsId, workStatus ->
            devices.updateWorkStatus(goodsId, workStatus)
        },
        onRefreshAfterWater = { refreshOrders ->
            devices.refresh(userInitiated = false)
            if (refreshOrders) orders.refresh(force = true, userInitiated = false)
        }
    )

    init {
        if (hasInitialToken) {
            devices.refresh(userInitiated = false)
            orders.refresh(force = false, userInitiated = false)
            auth.verifyTokenInBackground()
        } else {
            auth.warmupDns()
        }
    }

    fun buzz(ms: Int = 22, amplitude: Int = 120) = haptics.buzz(ms, amplitude)

    fun showToast(message: String) {
        _shell.update { it.copy(toast = ToastEvent(++toastCounter, message)) }
    }

    fun clearToast(id: Long) {
        _shell.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    fun selectTab(tab: MainTab) {
        _shell.update { it.copy(mainTab = tab) }
    }

    fun openSummary() {
        _shell.update { it.copy(summaryOpen = true) }
    }

    fun closeSummary() {
        _shell.update { it.copy(summaryOpen = false) }
    }

    fun enterDemoMode() {
        cancelAll()
        auth.setDemoIdentity()
        devices.loadDemo()
        orders.loadDemo()
        scan.close()
        water.reset()
        _shell.update {
            it.copy(
                screen = AppScreen.MAIN,
                mainTab = MainTab.DEVICES,
                demoMode = true,
                summaryOpen = false
            )
        }
    }

    fun logout() {
        cancelAll()
        if (!_shell.value.demoMode) {
            localStore.token = ""
        }
        auth.resetForLogin()
        devices.reset()
        orders.reset()
        scan.close()
        water.reset()
        _shell.value = AppShellState(screen = AppScreen.LOGIN)
    }

    private fun enterMainReal() {
        _shell.update {
            it.copy(
                screen = AppScreen.MAIN,
                mainTab = MainTab.DEVICES,
                demoMode = false,
                summaryOpen = false
            )
        }
        devices.refresh(userInitiated = false)
        orders.refresh(force = false, userInitiated = false)
    }

    private fun handleTokenExpired() {
        cancelAll()
        devices.reset()
        orders.reset()
        scan.close()
        water.reset()
        _shell.value = AppShellState(screen = AppScreen.LOGIN)
    }

    private fun cancelAll() {
        auth.cancel()
        devices.cancel()
        orders.cancel()
        scan.cancel()
        water.cancel()
    }

    companion object {
        fun factory(app: HotWaterApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HotWaterViewModel(
                        localStore = app.localStore,
                        ordersCache = app.ordersCache,
                        api = app.api,
                        haptics = app.haptics,
                        bleClient = app.bleClient
                    ) as T
                }
            }
    }
}
