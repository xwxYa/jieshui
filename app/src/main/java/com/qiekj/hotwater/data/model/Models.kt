package com.qiekj.hotwater.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val goodsId: String,
    val name: String,
    val shopName: String = "",
    val imei: String = "",
    val sn: String = "",
    val skuId: String = "",
    val price: String = "",
    val categoryName: String = "饮水机",
    val machineId: String = "",
    val shopId: String = "",
    val online: Boolean = true,
    val supportBle: Boolean = false,
    val workStatus: Int = 0
) {
    fun stateLabel(): String {
        if (!online) return "离线"
        return when (workStatus) {
            1 -> "空闲"
            2 -> "使用中"
            3 -> "别人在用"
            else -> "状态未知"
        }
    }

    fun subtitle(): String {
        val state = stateLabel()
        return if (price.isNotBlank()) "$price 元/100mL · $state" else state
    }
}

@Serializable
data class OrderItem(
    val orderNo: String,
    val machineName: String,
    val payPrice: Double,
    val createTime: String,
    val dateKey: String,
    val dateLabel: String,
    val timeLabel: String,
    val orderStatus: Int = 0
)

data class UserInfo(
    val nickname: String,
    val phone: String,
    val balance: String
)

data class WaterSyncState(
    val workStatus: Int,
    val status: Int,
    val amount: Double,
    val identify: String
)

data class UnlockPayload(
    val orderNo: String,
    val msgId: String = "",
    val bleCmd: String = "",
    val encryptionCmd: String = ""
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data object AuthExpired : ApiResult<Nothing>()
    data class ServerError(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
}

sealed class LoginOutcome {
    data object Success : LoginOutcome()
    data object BadPhone : LoginOutcome()
    data object NewAccountRejected : LoginOutcome()
    data class Failure(val message: String) : LoginOutcome()
}

sealed class TokenCheckResult {
    data object Valid : TokenCheckResult()
    data object Expired : TokenCheckResult()
    data object NoToken : TokenCheckResult()
    data object NetworkError : TokenCheckResult()
}
