package com.qiekj.hotwater.data.remote

import com.qiekj.hotwater.data.model.ApiResult
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.model.LoginOutcome
import com.qiekj.hotwater.data.model.OrderItem
import com.qiekj.hotwater.data.model.TokenCheckResult
import com.qiekj.hotwater.data.model.UnlockPayload
import com.qiekj.hotwater.data.model.UserInfo
import com.qiekj.hotwater.data.model.WaterSyncState
import com.qiekj.hotwater.domain.PhoneValidator
import org.json.JSONObject

class QiekjApi(
    private val http: QiekjHttpClient,
    private val onTokenUpdated: (token: String, phone: String) -> Unit = { _, _ -> },
    private val onTokenCleared: () -> Unit = {}
) {
    companion object {
        private const val PROMOTIONS =
            "[{\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-6\"}," +
                "{\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-7\"}," +
                "{\"assetId\":\"0\",\"oldPromotionId\":\"0\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-8\"}]"
    }

    suspend fun warmup() {
        http.warmupDns()
    }

    suspend fun checkToken(currentToken: String): TokenCheckResult {
        if (currentToken.isBlank()) return TokenCheckResult.NoToken
        return when (val r = http.post("/user/info")) {
            is ApiResult.Success -> {
                if (r.data.optJSONObject("data") != null) TokenCheckResult.Valid
                else TokenCheckResult.NetworkError
            }
            ApiResult.AuthExpired -> {
                onTokenCleared()
                TokenCheckResult.Expired
            }
            is ApiResult.ServerError,
            is ApiResult.NetworkError -> TokenCheckResult.NetworkError
        }
    }

    suspend fun userInfo(): ApiResult<UserInfo> {
        return when (val r = http.post("/user/info")) {
            is ApiResult.Success -> {
                val d = r.data.optJSONObject("data")
                    ?: return ApiResult.ServerError(r.data.optInt("code", -1), r.data.optString("msg", "获取用户信息失败"))
                ApiResult.Success(
                    UserInfo(
                        nickname = d.optString("userName").ifBlank { "未命名" },
                        phone = d.optString("phone"),
                        balance = d.optString("balance", "0.00")
                    )
                )
            }
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> r
        }
    }

    suspend fun sendCode(phone: String): ApiResult<Unit> {
        val clean = phone.trim()
        if (!PhoneValidator.isValidMainlandPhone(clean)) {
            return ApiResult.ServerError(-1, "这不像个手机号")
        }
        return when (val r = http.post("/common/sms/sendCode", mapOf("phone" to clean, "template" to "reg"))) {
            is ApiResult.Success -> {
                if (r.data.optInt("code", -1) == 0) ApiResult.Success(Unit)
                else ApiResult.ServerError(
                    r.data.optInt("code", -1),
                    r.data.optString("msg").ifBlank { "发不出去" }
                )
            }
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> ApiResult.NetworkError("发不出去，网络没通", r.cause)
        }
    }

    suspend fun verifyLogin(phone: String, code: String): LoginOutcome {
        val cleanPhone = phone.trim()
        val cleanCode = code.trim()
        if (!PhoneValidator.isValidMainlandPhone(cleanPhone)) return LoginOutcome.BadPhone

        return when (
            val r = http.post(
                "/user/reg",
                mapOf("phone" to cleanPhone, "verify" to cleanCode, "channel" to QiekjSigner.CHANNEL)
            )
        ) {
            is ApiResult.Success -> {
                val data = r.data.optJSONObject("data")
                    ?: return LoginOutcome.Failure(r.data.optString("msg").ifBlank { "验证码不对" })
                val token = data.optString("token")
                if (token.isBlank()) {
                    return LoginOutcome.Failure(r.data.optString("msg").ifBlank { "验证码不对" })
                }
                if (data.optBoolean("newRegister", false)) {
                    return LoginOutcome.NewAccountRejected
                }
                onTokenUpdated(token, cleanPhone)
                LoginOutcome.Success
            }
            ApiResult.AuthExpired -> LoginOutcome.Failure("登录态异常")
            is ApiResult.ServerError -> LoginOutcome.Failure(r.message)
            is ApiResult.NetworkError -> LoginOutcome.Failure(r.message)
        }
    }

    suspend fun scanDevice(code: String): Device? {
        for (key in listOf("SN", "IMEI")) {
            val r = http.post("/goods/scan/v2", mapOf(key to code))
            if (r is ApiResult.Success) {
                val d = r.data.optJSONObject("data") ?: continue
                val goodsId = d.optString("id")
                if (goodsId.isBlank()) continue
                return Device(
                    goodsId = goodsId,
                    name = "",
                    shopName = "",
                    imei = code,
                    sn = code,
                    categoryName = d.optString("categoryName", "饮水机"),
                    shopId = d.optString("shopId")
                )
            }
        }
        return null
    }

    suspend fun fillDeviceDetail(dev: Device): Device {
        val detailRes = http.post("/goods/normal/details", mapOf("goodsId" to dev.goodsId))
        val d = (detailRes as? ApiResult.Success)?.data?.optJSONObject("data") ?: return dev
        val skuRes = http.post("/goods/normal/skus", mapOf("goodsId" to dev.goodsId))
        val sku = (skuRes as? ApiResult.Success)?.data?.optJSONArray("data")?.optJSONObject(0)

        val realPrice = runCatching {
            val ext = sku?.optString("extAttr").orEmpty()
            if (ext.isBlank()) "" else {
                JSONObject(ext).optJSONArray("level")?.optJSONObject(0)?.optString("price").orEmpty()
            }
        }.getOrDefault("")

        return dev.copy(
            name = d.optString("name").ifBlank { dev.name },
            shopName = d.optString("orgName").ifBlank { dev.shopName },
            imei = d.optString("imei").ifBlank { dev.imei },
            machineId = d.optString("machineId").ifBlank { dev.machineId },
            shopId = d.optString("shopId").ifBlank { dev.shopId },
            skuId = sku?.optString("skuId").orEmpty(),
            price = realPrice.ifBlank { sku?.optString("price").orEmpty() },
            online = d.optInt("soldState", 1) == 1,
            supportBle = d.optInt("isBluetooth", 0) == 1 || d.optBoolean("isBluetooth", false)
        )
    }

    suspend fun waterSync(skuId: String): WaterSyncState? {
        if (skuId.isBlank()) return null
        val r = http.post("/goods/water/sync", mapOf("skuId" to skuId))
        val d = (r as? ApiResult.Success)?.data?.optJSONObject("data") ?: return null
        return WaterSyncState(
            workStatus = d.optInt("workStatus", 0),
            status = d.optInt("status", 0),
            amount = d.optString("amount", "0").toDoubleOrNull() ?: d.optDouble("amount", 0.0),
            identify = d.optString("identify")
        )
    }

    suspend fun unlock(skuId: String): ApiResult<UnlockPayload> {
        return when (
            val r = http.post(
                "/goods/ble/water/unlock",
                mapOf("skuId" to skuId, "promotions" to PROMOTIONS)
            )
        ) {
            is ApiResult.Success -> {
                val data = r.data.optJSONObject("data")
                val orderNo = data?.optString("orderNo").orEmpty()
                val msgId = data?.optString("msgId").orEmpty()
                val cmdObj = data?.optJSONObject("cmd")
                val bleCmd = cmdObj?.optString("bleCmd").orEmpty()
                    .ifBlank { data?.optString("bleCmd").orEmpty() }
                val encCmd = cmdObj?.optString("encryptionCmd").orEmpty()
                    .ifBlank { data?.optString("encryptionCmd").orEmpty() }
                if (orderNo.isNotBlank() || bleCmd.isNotBlank()) {
                    ApiResult.Success(UnlockPayload(orderNo, msgId, bleCmd, encCmd))
                } else {
                    ApiResult.ServerError(
                        r.data.optInt("code", -1),
                        r.data.optString("msg").ifBlank { "解锁失败" }
                    )
                }
            }
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> r
        }
    }

    suspend fun endWater(goodsId: String): ApiResult<Boolean> {
        if (goodsId.isBlank()) return ApiResult.ServerError(-1, "设备编号无效")
        return when (val r = http.post("/goods/endWater", mapOf("goodsId" to goodsId))) {
            is ApiResult.Success -> ApiResult.Success(r.data.optBoolean("data", false))
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> r
        }
    }

    suspend fun fetchBleCommand(
        type: Int,
        machineId: String,
        identify: String? = null
    ): ApiResult<String> {
        val params = buildMap {
            put("type", type.toString())
            put("machineId", machineId)
            if (!identify.isNullOrBlank()) put("identify", identify)
        }
        return when (val r = http.post("/machine/encryption/cmd", params)) {
            is ApiResult.Success -> {
                val d = r.data.optJSONObject("data") ?: r.data
                val cmd = d.optString("bleCmd")
                if (cmd.isNotBlank()) ApiResult.Success(cmd)
                else ApiResult.ServerError(
                    r.data.optInt("code", -1),
                    r.data.optString("msg").ifBlank { "获取蓝牙指令失败" }
                )
            }
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> r
        }
    }

    suspend fun reportBleData(
        encryptionData: String,
        imei: String,
        cmdCode: String = "0701"
    ): ApiResult<JSONObject> {
        return http.post(
            "/machine/bluetooth/report",
            mapOf(
                "encryptionData" to encryptionData,
                "imei" to imei,
                "cmdCode" to cmdCode
            )
        )
    }

    suspend fun fetchOrdersPage(page: Int, size: Int = 100): ApiResult<List<OrderItem>> {
        return when (
            val r = http.post(
                "/order/list",
                mapOf("page" to page.toString(), "pageSize" to size.toString(), "orderStatus" to "")
            )
        ) {
            is ApiResult.Success -> {
                val items = r.data.optJSONObject("data")?.optJSONArray("items")
                    ?: return ApiResult.ServerError(
                        r.data.optInt("code", -1),
                        r.data.optString("msg").ifBlank { "订单响应异常" }
                    )
                val out = ArrayList<OrderItem>(items.length())
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val createTime = o.optString("createTime")
                    val day = createTime.take(10)
                    val hm = if (createTime.length >= 16) createTime.substring(11, 16) else ""
                    val parts = day.split("-")
                    val mon = parts.getOrNull(1)?.trimStart('0').orEmpty()
                    val d = parts.getOrNull(2)?.trimStart('0').orEmpty()
                    out += OrderItem(
                        orderNo = o.optString("orderNo"),
                        machineName = o.optString("machineName").ifBlank { "未知设备" },
                        payPrice = o.optString("payPrice", "0.00").toDoubleOrNull() ?: 0.0,
                        createTime = createTime,
                        dateKey = day,
                        dateLabel = if (mon.isNotBlank() && d.isNotBlank()) "${mon}月${d}日" else day,
                        timeLabel = hm,
                        orderStatus = o.optInt("orderStatus", 0)
                    )
                }
                ApiResult.Success(out)
            }
            ApiResult.AuthExpired -> ApiResult.AuthExpired
            is ApiResult.ServerError -> r
            is ApiResult.NetworkError -> r
        }
    }
}
