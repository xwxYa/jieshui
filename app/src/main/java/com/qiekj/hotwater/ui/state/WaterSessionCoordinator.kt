package com.qiekj.hotwater.ui.state

import com.qiekj.hotwater.data.model.ApiResult
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.model.UnlockPayload
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.hardware.Haptics
import com.qiekj.hotwater.hardware.ble.BleExecutionResult
import com.qiekj.hotwater.hardware.ble.BleInboundFrame
import com.qiekj.hotwater.hardware.ble.QiekjBleClient
import com.qiekj.hotwater.ui.PollingAction
import com.qiekj.hotwater.ui.WaterPhase
import com.qiekj.hotwater.ui.WaterPollingReducer
import com.qiekj.hotwater.ui.WaterSessionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WaterSessionCoordinator(
    private val scope: CoroutineScope,
    private val api: QiekjApi,
    private val haptics: Haptics,
    private val bleClient: QiekjBleClient,
    private val isDemoMode: () -> Boolean,
    private val showToast: (String) -> Unit,
    private val onTokenExpired: () -> Unit = {},
    private val onDeviceWorkStatusChanged: (goodsId: String, workStatus: Int) -> Unit,
    private val onRefreshAfterWater: (refreshOrders: Boolean) -> Unit
) {
    private val _state = MutableStateFlow(WaterSessionUiState())
    val state: StateFlow<WaterSessionUiState> = _state.asStateFlow()

    /**
     * 按 goodsId 缓存未结束的活跃会话（已解锁 / 出水中），避免用户点返回后重进时丢失状态。
     */
    private val activeSessions = mutableMapOf<String, WaterSessionUiState>()

    private var unlockJob: Job? = null
    private var bleJob: Job? = null
    private var pollJob: Job? = null
    private var probeJob: Job? = null

    fun open(device: Device) {
        cancel()
        val initial = WaterPollingReducer.resolveInitialSession(
            device = device,
            cachedActive = activeSessions[device.goodsId]
        )
        _state.value = initial

        when (initial.phase) {
            WaterPhase.UNLOCKED, WaterPhase.DISPENSING -> {
                activeSessions[device.goodsId] = initial
                if (isDemoMode()) {
                    if (initial.phase == WaterPhase.UNLOCKED) {
                        startDemoPolling(device.goodsId)
                    }
                } else {
                    startRealPolling(device, initiallySawActive = initial.phase == WaterPhase.DISPENSING)
                }
            }
            WaterPhase.LOCKED -> {
                if (!isDemoMode() && device.online) {
                    probeActiveStateOnce(device)
                }
            }
            else -> Unit
        }
    }

    fun close() {
        val cur = _state.value
        val dev = cur.device
        val isActiveSession = cur.phase == WaterPhase.UNLOCKED || cur.phase == WaterPhase.DISPENSING
        if (dev != null) {
            if (isActiveSession) {
                activeSessions[dev.goodsId] = cur
                onDeviceWorkStatusChanged(dev.goodsId, 2)
            } else {
                activeSessions.remove(dev.goodsId)
            }
        }
        cancel()
        _state.value = WaterSessionUiState()
        if (!isDemoMode()) {
            onRefreshAfterWater(isActiveSession)
        }
    }

    fun unlock() {
        val session = _state.value
        val dev = session.device ?: return
        if (session.phase != WaterPhase.LOCKED) return
        if (!dev.online) {
            haptics.buzz(28)
            showToast("这台设备离线了")
            return
        }

        probeJob?.cancel()
        _state.update {
            it.copy(phase = WaterPhase.WAKING, syncStatusText = "正在唤醒机器…")
        }

        unlockJob?.cancel()
        unlockJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            if (isDemoMode()) {
                delay(880)
                if (_state.value.device?.goodsId != dev.goodsId) return@launch
                onUnlockedSuccess(dev.goodsId, UnlockPayload(orderNo = "DEMO"))
                startDemoPolling(dev.goodsId)
                return@launch
            }

            when (val r = api.unlock(dev.skuId)) {
                is ApiResult.Success -> {
                    val elapsed = System.currentTimeMillis() - startedAt
                    val remain = (880L - elapsed).coerceAtLeast(0L)
                    if (remain > 0L) delay(remain)
                    if (_state.value.device?.goodsId != dev.goodsId) return@launch
                    onUnlockedSuccess(dev.goodsId, r.data)
                    startRealPolling(dev, initiallySawActive = false)
                }
                ApiResult.AuthExpired -> {
                    if (_state.value.device?.goodsId == dev.goodsId) {
                        resetToLocked(dev.goodsId, "登录态已失效，请重新登录")
                        onTokenExpired()
                    }
                }
                is ApiResult.ServerError -> {
                    if (_state.value.device?.goodsId == dev.goodsId) {
                        resetToLocked(dev.goodsId, r.message, showBle = dev.supportBle || dev.imei.isNotBlank())
                    }
                }
                is ApiResult.NetworkError -> {
                    if (_state.value.device?.goodsId == dev.goodsId) {
                        resetToLocked(dev.goodsId, "${r.message}，可尝试蓝牙近场解锁", showBle = true)
                    }
                }
            }
        }
    }

    fun triggerBleFallback() {
        val session = _state.value
        val dev = session.device ?: return
        if (session.bleBusy) return

        if (isDemoMode()) {
            showToast("演示模式下不触碰真实蓝牙硬件")
            return
        }
        if (!bleClient.isBluetoothEnabled()) {
            showToast("请先打开手机蓝牙")
            return
        }

        _state.update {
            it.copy(bleBusy = true, syncStatusText = "正在通过蓝牙直连饮水机…")
        }

        bleJob?.cancel()
        bleJob = scope.launch {
            var cmdHex = session.bleCmd
            if (cmdHex.isBlank() && dev.machineId.isNotBlank()) {
                val cmdRes = api.fetchBleCommand(type = 1, machineId = dev.machineId)
                if (cmdRes is ApiResult.Success) cmdHex = cmdRes.data
            }
            if (_state.value.device?.goodsId != dev.goodsId) return@launch
            if (cmdHex.isBlank()) {
                _state.update {
                    it.copy(
                        bleBusy = false,
                        syncStatusText = "服务端未下发蓝牙加密指令，请检查网络后重试"
                    )
                }
                showToast("没拿到蓝牙加密指令")
                return@launch
            }

            val targetId = dev.imei.ifBlank { dev.sn }
            when (val res = bleClient.sendCommand(targetId, cmdHex)) {
                is BleExecutionResult.Reported -> {
                    if (_state.value.device?.goodsId != dev.goodsId) return@launch
                    when (val frame = res.frame) {
                        is BleInboundFrame.EncryptedReport -> {
                            val cmdCode = if (frame.cmdType == "07") "0105" else "0701"
                            api.reportBleData(frame.payloadHex, targetId, cmdCode)
                            finishBleUnlock(dev, session.orderNo, cmdHex)
                        }
                        is BleInboundFrame.UpReport -> {
                            api.reportBleData(frame.payloadHex, targetId, "0701")
                            finishBleUnlock(dev, session.orderNo, cmdHex)
                        }
                        BleInboundFrame.CloudDeduplicated -> {
                            finishBleUnlock(dev, session.orderNo, cmdHex)
                        }
                        is BleInboundFrame.Unknown -> {
                            _state.update { it.copy(bleBusy = false) }
                            showToast("蓝牙已发送，请尝试在机器上按键")
                        }
                    }
                }
                BleExecutionResult.WrittenNoReply -> {
                    if (_state.value.device?.goodsId != dev.goodsId) return@launch
                    finishBleUnlock(dev, session.orderNo, cmdHex)
                }
                is BleExecutionResult.Failed -> {
                    if (_state.value.device?.goodsId != dev.goodsId) return@launch
                    _state.update { it.copy(bleBusy = false, syncStatusText = res.reason) }
                    showToast(res.reason)
                }
            }
        }
    }

    fun endWater() {
        val session = _state.value
        val dev = session.device ?: return
        if (session.phase != WaterPhase.UNLOCKED && session.phase != WaterPhase.DISPENSING) return

        pollJob?.cancel()
        activeSessions.remove(dev.goodsId)
        _state.update { it.copy(phase = WaterPhase.ENDING, syncStatusText = "正在结束本次接水…") }

        scope.launch {
            if (isDemoMode()) {
                delay(280)
                onDeviceWorkStatusChanged(dev.goodsId, 1)
                if (_state.value.device?.goodsId != dev.goodsId) return@launch
                _state.update {
                    it.copy(
                        phase = WaterPhase.LOCKED,
                        liveAmount = 0.0,
                        syncStatusText = "解锁后机器上的按钮才会生效，解锁本身不扣费"
                    )
                }
                showToast("已结束接水（演示）")
                return@launch
            }

            val res = api.endWater(dev.goodsId)
            val ok = res is ApiResult.Success && res.data
            onDeviceWorkStatusChanged(dev.goodsId, 1)
            if (_state.value.device?.goodsId == dev.goodsId) {
                _state.update {
                    it.copy(
                        phase = WaterPhase.LOCKED,
                        liveAmount = 0.0,
                        syncStatusText = "解锁后机器上的按钮才会生效，解锁本身不扣费"
                    )
                }
            }
            showToast(if (ok) "已结束接水" else "没通知到机器，请到机器上按一下停止")
            delay(1500)
            onRefreshAfterWater(true)
        }
    }

    fun reset() {
        cancel()
        activeSessions.clear()
        _state.value = WaterSessionUiState()
    }

    fun cancel() {
        unlockJob?.cancel()
        bleJob?.cancel()
        pollJob?.cancel()
        probeJob?.cancel()
    }

    private fun probeActiveStateOnce(dev: Device) {
        probeJob?.cancel()
        probeJob = scope.launch {
            val sync = api.waterSync(dev.skuId) ?: return@launch
            val cur = _state.value
            if (cur.device?.goodsId != dev.goodsId || cur.phase != WaterPhase.LOCKED) return@launch
            val verdict = WaterPollingReducer.evaluate(sync, alreadySawActive = false)
            if (verdict.action == PollingAction.DISPENSING) {
                val amtText = if (sync.amount > 0.0) {
                    "出水中 · 当前已用 ¥${"%.2f".format(sync.amount)}"
                } else {
                    "机器正在使用中 · 松开后自动结算或点下方结束"
                }
                val next = cur.copy(
                    phase = WaterPhase.DISPENSING,
                    liveAmount = sync.amount,
                    syncStatusText = amtText
                )
                _state.value = next
                activeSessions[dev.goodsId] = next
                onDeviceWorkStatusChanged(dev.goodsId, 2)
                startRealPolling(dev, initiallySawActive = true)
            }
        }
    }

    private fun finishBleUnlock(dev: Device, orderNo: String, cmdHex: String) {
        _state.update { it.copy(bleBusy = false) }
        onUnlockedSuccess(dev.goodsId, UnlockPayload(orderNo = orderNo, bleCmd = cmdHex))
        startRealPolling(dev, initiallySawActive = false)
    }

    private fun onUnlockedSuccess(expectedGoodsId: String, payload: UnlockPayload) {
        if (_state.value.device?.goodsId != expectedGoodsId) return
        haptics.buzz(26, 150)
        _state.update {
            it.copy(
                phase = WaterPhase.UNLOCKED,
                orderNo = payload.orderNo,
                bleCmd = payload.bleCmd.ifBlank { payload.encryptionCmd },
                syncStatusText = "现在到机器上按接水按钮，接完自动结算或点下方结束",
                showBleFallback = payload.bleCmd.isNotBlank() || it.device?.supportBle == true
            )
        }
        activeSessions[expectedGoodsId] = _state.value
        onDeviceWorkStatusChanged(expectedGoodsId, 2)
        showToast("解锁成功 · 请在出水口点按开始")
    }

    private fun resetToLocked(goodsId: String, errorMsg: String, showBle: Boolean = false) {
        pollJob?.cancel()
        activeSessions.remove(goodsId)
        haptics.buzz(35)
        _state.update {
            it.copy(
                phase = WaterPhase.LOCKED,
                syncStatusText = "解锁后机器上的按钮才会生效，解锁本身不扣费",
                showBleFallback = showBle
            )
        }
        showToast(errorMsg)
    }

    private fun startDemoPolling(expectedGoodsId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            delay(2500)
            val cur = _state.value
            if (cur.device?.goodsId == expectedGoodsId && cur.phase == WaterPhase.UNLOCKED) {
                val next = cur.copy(
                    phase = WaterPhase.DISPENSING,
                    liveAmount = 0.12,
                    syncStatusText = "演示出水中 · 已实时同步状态"
                )
                _state.value = next
                activeSessions[expectedGoodsId] = next
            }
        }
    }

    private fun startRealPolling(dev: Device, initiallySawActive: Boolean) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var sawActive = initiallySawActive
            var failStreak = 0
            while (isActive && _state.value.device?.goodsId == dev.goodsId) {
                delay(1000)
                val state = api.waterSync(dev.skuId)
                if (_state.value.device?.goodsId != dev.goodsId) return@launch
                if (state == null) {
                    failStreak++
                    if (failStreak >= 4) {
                        _state.update {
                            it.copy(
                                showBleFallback = true,
                                syncStatusText = "机器状态同步较慢，可继续按键或使用蓝牙直连"
                            )
                        }
                    }
                    continue
                }
                failStreak = 0
                val verdict = WaterPollingReducer.evaluate(state, sawActive)
                sawActive = verdict.nextSawActive

                when (verdict.action) {
                    PollingAction.DISPENSING -> {
                        val amtText = if (state.amount > 0.0) {
                            "出水中 · 当前已用 ¥${"%.2f".format(state.amount)}"
                        } else {
                            "机器正在出水中 · 松开后将自动结算"
                        }
                        _state.update {
                            it.copy(
                                phase = WaterPhase.DISPENSING,
                                liveAmount = state.amount,
                                syncStatusText = amtText
                            )
                        }
                        activeSessions[dev.goodsId] = _state.value
                        onDeviceWorkStatusChanged(dev.goodsId, 2)
                    }
                    PollingAction.FINISHED -> {
                        activeSessions.remove(dev.goodsId)
                        onDeviceWorkStatusChanged(dev.goodsId, 1)
                        haptics.buzz(22)
                        _state.update {
                            it.copy(
                                phase = WaterPhase.LOCKED,
                                liveAmount = 0.0,
                                syncStatusText = "解锁后机器上的按钮才会生效，解锁本身不扣费"
                            )
                        }
                        showToast("接水已完成，账单已更新")
                        delay(1200)
                        onRefreshAfterWater(true)
                        return@launch
                    }
                    PollingAction.KEEP_WAITING -> Unit
                }
            }
        }
    }
}
