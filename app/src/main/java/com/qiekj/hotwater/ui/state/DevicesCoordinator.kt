package com.qiekj.hotwater.ui.state

import com.qiekj.hotwater.data.DemoData
import com.qiekj.hotwater.data.local.LocalStore
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.ui.DevicesUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DevicesCoordinator(
    private val scope: CoroutineScope,
    private val localStore: LocalStore,
    private val api: QiekjApi,
    private val isDemoMode: () -> Boolean,
    private val showToast: (String) -> Unit,
    initialDevices: List<Device>,
    initialLoaded: Boolean
) {
    private val _state = MutableStateFlow(
        DevicesUiState(
            devices = initialDevices,
            devicesLoaded = initialLoaded
        )
    )
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(userInitiated: Boolean = false) {
        if (isDemoMode()) {
            if (userInitiated) {
                scope.launch {
                    _state.update { it.copy(devicesRefreshing = true) }
                    delay(350)
                    _state.update { it.copy(devicesRefreshing = false) }
                    showToast("演示设备状态已刷新")
                }
            }
            return
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (userInitiated) _state.update { it.copy(devicesRefreshing = true) }
            val local = localStore.loadDevices()
            _state.update { it.copy(devices = local, devicesLoaded = true) }
            if (local.isEmpty()) {
                _state.update { it.copy(devicesRefreshing = false) }
                return@launch
            }

            val updated = coroutineScope {
                local.map { dev ->
                    async {
                        val sync = api.waterSync(dev.skuId)
                        if (sync != null) dev.copy(workStatus = sync.workStatus) else dev
                    }
                }.awaitAll()
            }
            if (!isDemoMode()) {
                _state.update {
                    it.copy(
                        devices = updated,
                        devicesLoaded = true,
                        devicesRefreshing = false
                    )
                }
            }
        }
    }

    fun addDeviceToTop(device: Device) {
        if (isDemoMode()) {
            _state.update { st ->
                st.copy(devices = listOf(device) + st.devices.filterNot { it.goodsId == device.goodsId })
            }
        } else {
            val saved = localStore.addDeviceToTop(device)
            _state.update { it.copy(devices = saved, devicesLoaded = true) }
            refresh(userInitiated = false)
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val list = _state.value.devices.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update { it.copy(devices = list) }
        if (!isDemoMode()) {
            localStore.reorderDevices(list.map { it.goodsId })
        }
    }

    fun persistRemove(goodsId: String) {
        if (!isDemoMode()) {
            localStore.removeDevice(goodsId)
        }
    }

    fun finalizeRemove(goodsId: String) {
        _state.update { st -> st.copy(devices = st.devices.filterNot { it.goodsId == goodsId }) }
        if (!isDemoMode()) {
            localStore.removeDevice(goodsId)
        }
    }

    fun updateWorkStatus(goodsId: String, workStatus: Int) {
        _state.update { st ->
            st.copy(
                devices = st.devices.map { dev ->
                    if (dev.goodsId == goodsId) dev.copy(workStatus = workStatus) else dev
                }
            )
        }
    }

    fun loadDemo() {
        cancel()
        _state.value = DevicesUiState(
            devices = DemoData.devices,
            devicesLoaded = true,
            devicesRefreshing = false
        )
    }

    fun reset() {
        cancel()
        _state.value = DevicesUiState()
    }

    fun cancel() {
        refreshJob?.cancel()
    }
}
