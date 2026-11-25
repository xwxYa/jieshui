package com.qiekj.hotwater

import android.app.Application
import com.qiekj.hotwater.data.local.LocalStore
import com.qiekj.hotwater.data.local.OrdersCache
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.data.remote.QiekjHttpClient
import com.qiekj.hotwater.hardware.Haptics
import com.qiekj.hotwater.hardware.ble.QiekjBleClient

class HotWaterApp : Application() {
    lateinit var localStore: LocalStore
        private set
    lateinit var ordersCache: OrdersCache
        private set
    lateinit var api: QiekjApi
        private set
    lateinit var haptics: Haptics
        private set
    lateinit var bleClient: QiekjBleClient
        private set

    override fun onCreate() {
        super.onCreate()
        localStore = LocalStore(this)
        ordersCache = OrdersCache(
            storageDir = filesDir,
            accountProvider = { localStore.phone }
        )
        val http = QiekjHttpClient(
            tokenProvider = { localStore.token },
            versionProvider = { localStore.apiVersion }
        )
        api = QiekjApi(
            http = http,
            onTokenUpdated = { token, phone ->
                localStore.token = token
                localStore.phone = phone
            },
            onTokenCleared = {
                localStore.token = ""
            }
        )
        haptics = Haptics(this)
        bleClient = QiekjBleClient(this)
    }
}
