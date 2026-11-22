package com.qiekj.hotwater.ui.state

import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.domain.QrParser
import com.qiekj.hotwater.ui.ScanLookupState
import com.qiekj.hotwater.ui.ScanUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanCoordinator(
    private val scope: CoroutineScope,
    private val api: QiekjApi,
    private val isDemoMode: () -> Boolean,
    private val showToast: (String) -> Unit,
    private val onDeviceConfirmed: (Device) -> Unit
) {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun open() {
        lookupJob?.cancel()
        _state.value = ScanUiState(scanOpen = true)
    }

    fun resetToIdle() {
        lookupJob?.cancel()
        _state.update {
            it.copy(
                scanState = ScanLookupState.IDLE,
                scanMessage = "把机器上的二维码放进取景框",
                pendingScanDevice = null
            )
        }
    }

    fun close() {
        lookupJob?.cancel()
        _state.value = ScanUiState(scanOpen = false)
    }

    fun lookupFromQr(raw: String) {
        if (_state.value.scanState == ScanLookupState.SEARCHING) return
        _state.update {
            it.copy(
                scanState = ScanLookupState.SEARCHING,
                scanMessage = "扫到了，正在查这台机器…"
            )
        }
        lookupInternal(raw, fromManual = false)
    }

    fun lookupByManualNo(no: String) {
        val clean = no.trim()
        if (clean.length < 6) {
            showToast("设备号不止这么短")
            return
        }
        _state.update {
            it.copy(
                scanState = ScanLookupState.SEARCHING,
                scanMessage = "正在查这台机器…"
            )
        }
        lookupInternal(clean, fromManual = true)
    }

    fun confirmAdd() {
        val dev = _state.value.pendingScanDevice ?: return
        close()
        onDeviceConfirmed(dev)
        showToast("已添加 · ${dev.name.ifBlank { "饮水机" }}")
    }

    fun cancel() {
        lookupJob?.cancel()
    }

    private fun lookupInternal(raw: String, fromManual: Boolean) {
        lookupJob?.cancel()
        lookupJob = scope.launch {
            if (isDemoMode()) {
                delay(450)
                if (!_state.value.scanOpen) return@launch
                val fake = Device(
                    goodsId = "demo_scan_${System.currentTimeMillis() % 1000}",
                    name = "1号楼南热水",
                    shopName = "教学楼直饮水",
                    imei = raw.take(15).ifBlank { "860000000000099" },
                    sn = raw.take(15).ifBlank { "860000000000099" },
                    skuId = "demo_sku",
                    price = "0.011",
                    online = true,
                    supportBle = true,
                    workStatus = 1
                )
                _state.update {
                    it.copy(
                        scanState = ScanLookupState.FOUND,
                        scanMessage = if (fromManual) "查到了这台机器" else "扫到了这台机器",
                        pendingScanDevice = fake
                    )
                }
                return@launch
            }

            val cands = QrParser.extractCandidates(raw)
            if (cands.isEmpty()) {
                handleFailure("看不出这是哪台机器", fromManual)
                return@launch
            }

            var errMsg = "查不到这台机器"
            for (c in cands) {
                val scanned = api.scanDevice(c) ?: continue
                val detailed = api.fillDeviceDetail(scanned)
                if (detailed.goodsId.isBlank()) continue
                if (detailed.skuId.isBlank()) {
                    errMsg = "这台机器没开热水规格"
                    continue
                }
                val sync = api.waterSync(detailed.skuId)
                val ready = if (sync != null) detailed.copy(workStatus = sync.workStatus) else detailed
                if (!_state.value.scanOpen) return@launch
                _state.update {
                    it.copy(
                        scanState = ScanLookupState.FOUND,
                        scanMessage = if (fromManual) "查到了这台机器" else "扫到了这台机器",
                        pendingScanDevice = ready
                    )
                }
                return@launch
            }
            handleFailure(errMsg, fromManual)
        }
    }

    private fun handleFailure(msg: String, fromManual: Boolean) {
        if (!_state.value.scanOpen) return
        if (fromManual) {
            showToast(msg)
            resetToIdle()
        } else {
            _state.update {
                it.copy(
                    scanState = ScanLookupState.ERROR,
                    scanMessage = "$msg —— 3 秒后重试"
                )
            }
            lookupJob = scope.launch {
                delay(3000)
                if (_state.value.scanOpen && _state.value.scanState == ScanLookupState.ERROR) {
                    resetToIdle()
                }
            }
        }
    }
}
