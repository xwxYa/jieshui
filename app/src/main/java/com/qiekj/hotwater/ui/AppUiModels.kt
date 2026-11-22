package com.qiekj.hotwater.ui

import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.model.WaterSyncState
import com.qiekj.hotwater.domain.OrderAnalytics
import com.qiekj.hotwater.domain.OrderSummaryStats

enum class AppScreen {
    LOGIN,
    MAIN
}

enum class MainTab {
    DEVICES,
    ORDERS
}

enum class WaterPhase {
    LOCKED,
    WAKING,
    UNLOCKED,
    DISPENSING,
    ENDING
}

enum class ScanLookupState {
    IDLE,
    SEARCHING,
    FOUND,
    ERROR
}

data class ToastEvent(val id: Long, val message: String)

data class AuthUiState(
    val phoneInput: String = "",
    val codeInput: String = "",
    val loggedInPhone: String = "",
    val sendCodeCountdown: Int = 0,
    val sendingCode: Boolean = false,
    val sendCodeSuccessTick: Int = 0,
    val loggingIn: Boolean = false,
    val loginSuccessTick: Int = 0,
    val loginBubbleText: String? = null,
    val loginBubbleError: Boolean = false,
    val loginBubbleTick: Int = 0,
    val phoneShakeTick: Int = 0,
    val codeShakeTick: Int = 0
)

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val devicesLoaded: Boolean = false,
    val devicesRefreshing: Boolean = false
)

data class ScanUiState(
    val scanOpen: Boolean = false,
    val scanState: ScanLookupState = ScanLookupState.IDLE,
    val scanMessage: String = "把机器上的二维码放进取景框",
    val pendingScanDevice: Device? = null
)

data class WaterSessionUiState(
    val device: Device? = null,
    val phase: WaterPhase = WaterPhase.LOCKED,
    val orderNo: String = "",
    val bleCmd: String = "",
    val liveAmount: Double = 0.0,
    val syncStatusText: String = "解锁后机器上的按钮才会生效，解锁本身不扣费",
    val showBleFallback: Boolean = false,
    val bleBusy: Boolean = false
)

data class OrdersUiState(
    val ordersLoading: Boolean = false,
    val ordersRefreshing: Boolean = false,
    val orderStats: OrderSummaryStats = OrderAnalytics.analyze(emptyList()),
    val selectedYear: Int? = null,
    val expandedMonths: Set<Int> = emptySet()
)

data class AppShellState(
    val screen: AppScreen = AppScreen.LOGIN,
    val mainTab: MainTab = MainTab.DEVICES,
    val demoMode: Boolean = false,
    val summaryOpen: Boolean = false,
    val toast: ToastEvent? = null
)

enum class PollingAction {
    KEEP_WAITING,
    DISPENSING,
    FINISHED
}

data class PollingDecision(
    val action: PollingAction,
    val nextSawActive: Boolean
)

object WaterPollingReducer {
    fun evaluate(state: WaterSyncState, alreadySawActive: Boolean): PollingDecision {
        if (state.status == 1 || state.workStatus == 2) {
            return PollingDecision(PollingAction.DISPENSING, nextSawActive = true)
        }
        if (state.status == 3 || state.status == 5) {
            return PollingDecision(PollingAction.FINISHED, nextSawActive = true)
        }
        if (alreadySawActive && state.workStatus == 1) {
            return PollingDecision(PollingAction.FINISHED, nextSawActive = true)
        }
        return PollingDecision(PollingAction.KEEP_WAITING, nextSawActive = alreadySawActive)
    }

    /**
     * 打开设备接水页时决定初始会话状态：
     * 1. 若该设备存在本地未结束的活跃会话（刚解锁或正在出水后返回过列表），原样无损恢复；
     * 2. 若设备本身已上报 workStatus == 2（我自己的订单正在使用中），直接进入 DISPENSING 态；
     * 3. 否则以 LOCKED 态打开。
     */
    fun resolveInitialSession(
        device: Device,
        cachedActive: WaterSessionUiState?
    ): WaterSessionUiState {
        if (cachedActive != null &&
            (cachedActive.phase == WaterPhase.UNLOCKED || cachedActive.phase == WaterPhase.DISPENSING)
        ) {
            return cachedActive.copy(device = device)
        }
        if (device.online && device.workStatus == 2) {
            return WaterSessionUiState(
                device = device,
                phase = WaterPhase.DISPENSING,
                syncStatusText = "机器正在使用中 · 松开后自动结算或点下方结束",
                showBleFallback = device.supportBle
            )
        }
        return WaterSessionUiState(
            device = device,
            phase = WaterPhase.LOCKED,
            syncStatusText = "解锁后机器上的按钮才会生效，解锁本身不扣费",
            showBleFallback = device.supportBle
        )
    }
}
