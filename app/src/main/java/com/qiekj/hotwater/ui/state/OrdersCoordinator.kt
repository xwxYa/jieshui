package com.qiekj.hotwater.ui.state

import com.qiekj.hotwater.data.DemoData
import com.qiekj.hotwater.data.local.OrdersCache
import com.qiekj.hotwater.data.model.ApiResult
import com.qiekj.hotwater.data.model.OrderItem
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.domain.OrderAnalytics
import com.qiekj.hotwater.ui.OrdersUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrdersCoordinator(
    private val scope: CoroutineScope,
    private val ordersCache: OrdersCache,
    private val api: QiekjApi,
    private val isDemoMode: () -> Boolean,
    private val showToast: (String) -> Unit
) {
    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null

    fun refresh(force: Boolean, userInitiated: Boolean = false) {
        if (isDemoMode()) {
            if (userInitiated) {
                scope.launch {
                    _state.update { it.copy(ordersRefreshing = true) }
                    delay(350)
                    _state.update { it.copy(ordersRefreshing = false) }
                    showToast("演示账单已刷新")
                }
            }
            return
        }
        fetchJob?.cancel()
        fetchJob = scope.launch {
            if (userInitiated) _state.update { it.copy(ordersRefreshing = true) }
            val cached = ordersCache.read()
            if (!force && cached != null) {
                applyOrders(cached)
                if (ordersCache.isFresh()) {
                    _state.update { it.copy(ordersRefreshing = false) }
                    return@launch
                }
            } else if (_state.value.orderStats.totalCount == 0) {
                _state.update { it.copy(ordersLoading = true) }
            }

            val all = ArrayList<OrderItem>()
            var page = 1
            var interrupted = false
            while (page <= 60) {
                when (val r = api.fetchOrdersPage(page = page, size = 100)) {
                    is ApiResult.Success -> {
                        if (r.data.isEmpty()) break
                        all += r.data
                        if (r.data.size < 100) break
                        page++
                    }
                    ApiResult.AuthExpired,
                    is ApiResult.ServerError,
                    is ApiResult.NetworkError -> {
                        interrupted = true
                        break
                    }
                }
            }

            _state.update { it.copy(ordersLoading = false, ordersRefreshing = false) }
            if (interrupted) {
                if (cached == null) {
                    applyOrders(emptyList())
                } else if (force) {
                    showToast("网络波动，当前展示本地缓存账单")
                }
                return@launch
            }
            if (all.isEmpty()) {
                if (cached == null) applyOrders(emptyList())
                return@launch
            }
            ordersCache.write(all)
            applyOrders(all)
        }
    }

    fun selectYear(year: Int) {
        val st = _state.value
        if (st.selectedYear == year) return
        val yearGroup = st.orderStats.years.firstOrNull { it.year == year } ?: return
        val firstMonth = yearGroup.months.firstOrNull()?.month?.let { setOf(it) }.orEmpty()
        _state.update { it.copy(selectedYear = year, expandedMonths = firstMonth) }
    }

    fun toggleMonth(month: Int) {
        _state.update { st ->
            val next = if (month in st.expandedMonths) st.expandedMonths - month else st.expandedMonths + month
            st.copy(expandedMonths = next)
        }
    }

    fun loadDemo() {
        cancel()
        val stats = OrderAnalytics.analyze(DemoData.orders)
        val firstYear = stats.years.firstOrNull()
        val defaultOpenMon = firstYear?.months?.firstOrNull()?.month?.let { setOf(it) }.orEmpty()
        _state.value = OrdersUiState(
            ordersLoading = false,
            ordersRefreshing = false,
            orderStats = stats,
            selectedYear = firstYear?.year,
            expandedMonths = defaultOpenMon
        )
    }

    fun reset() {
        cancel()
        _state.value = OrdersUiState()
    }

    fun cancel() {
        fetchJob?.cancel()
    }

    private fun applyOrders(items: List<OrderItem>) {
        val stats = OrderAnalytics.analyze(items)
        _state.update { st ->
            val targetYear = st.selectedYear?.takeIf { y -> stats.years.any { it.year == y } }
                ?: stats.years.firstOrNull()?.year
            val yearGroup = stats.years.firstOrNull { it.year == targetYear }
            val expanded = if (st.selectedYear == targetYear && st.expandedMonths.isNotEmpty()) {
                st.expandedMonths
            } else {
                yearGroup?.months?.firstOrNull()?.month?.let { setOf(it) }.orEmpty()
            }
            st.copy(
                ordersLoading = false,
                orderStats = stats,
                selectedYear = targetYear,
                expandedMonths = expanded
            )
        }
    }
}
