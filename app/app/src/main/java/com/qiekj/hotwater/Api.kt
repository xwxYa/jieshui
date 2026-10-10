package com.qiekj.hotwater

import org.json.JSONObject

/** 设备(饮水机) */
data class Device(
    val goodsId: String,
    val name: String,
    val shopName: String,
    val imei: String,
    val sn: String,
    val skuId: String = "",
    val price: String = "",
    val categoryName: String = "饮水机",
    val machineId: String = "",
    val shopId: String = "",
    val online: Boolean = true,
)

/** 订单 */
data class Order(
    val orderNo: String,
    val machineName: String,
    val payPrice: String,
    val createTime: String,
    val orderStatus: Int,
)

data class UserInfo(
    val nickname: String,
    val phone: String,
    val balance: String,
)

/**
 * 业务接口层。
 *
 * ⚠️ 本 App 只实现「查询」类接口。
 *    出水(goods/ble/water/unlock)、绑支付(payChannelRoute/addUserAfterPayChannel)、
 *    下单(trade/create、order/add)一律不实现 —— 见 README 的安全说明。
 */
object Api {

    /* ---------- 解锁 ---------- */

    /**
     * ⚠️ 解锁机器 —— 但**不会出水**。
     *
     * 出水是人在饮水机上按物理按键才发生的：服务端通道（machine 那一族接口）和蓝牙通道
     * 都只负责「解锁」，没有任何接口能直接启动出水。详见 `docs/API.md`。
     * 调它的后果是：机器进入「已解锁」状态，并返回一个 orderNo。
     *
     * 那为什么仍然不许在自动流程里调它？
     * 因为解锁后这个口子就开着 —— 之后谁来按一下机器，水都记在**这个账号**上。
     * App 自己不出水，但会把「出水」这个能力交给一台公共机器。
     *
     * 只能在用户亲手按下「解锁设备」时执行。
     *
     * @return orderNo to msg,orderNo 非空表示成功。
     */
    fun unlock(skuId: String): Pair<String, String> {
        val r = Net.post(
            "/goods/ble/water/unlock",
            mapOf("skuId" to skuId, "promotions" to PROMOTIONS)
        ) ?: return "" to "网络失败"
        val no = r.optJSONObject("data")?.optString("orderNo").orEmpty()
        return no to r.optString("msg")
    }

    /* ---------- 登录 ---------- */

    /**
     * 发验证码。成功返回空串，失败返回**服务端自己说的话**。
     *
     * 为什么不返回 Boolean：服务端会区分「手机号格式不对」「今日已达上限」
     * 「发送太频繁」，这些对用户都是有意义的 —— 原来一律糊成「发不出去，
     * 检查一下手机号」，号明明是对的，看着只会以为程序坏了。
     */
    fun sendCode(phone: String): String {
        val r = Net.post("/common/sms/sendCode", mapOf("phone" to phone, "template" to "reg"))
            ?: return "发不出去，网络没通"
        if (r.optInt("code") == 0) return ""
        return r.optString("msg").ifBlank { "发不出去" }
    }

    /**
     * 登录。返回 "ok" / "new" / "nosend" / "fail"。
     *
     * ⚠️ 这个接口是 /user/reg（注册接口），有个坑：
     *    **不存在**的手机号 + 任意验证码 → 当场新建一个空账号，并照发有效 token。
     *    所以「拿到 token」不算数：newRegister 为 true 说明是刚建的空壳，拒掉。
     *
     *    已经真实注册过的号会不会被假码登进去？不会 —— 实测：
     *    已注册的真号 + 假码 → {"code":1001,"msg":"短信验证码不正确"}。
     *    （这里不写具体号码 —— 这份代码要进仓库，号码是个人信息。）
     *    能绕过的只有 12345678910 这种**无效号段**（123 不是有效号段，
     *    服务端发不出短信索性不校验），那种号登进去也是个空壳，没危害。
     *    所以登不登得进去这一关，交给服务端判断，客户端不拦。
     */
    fun verify(phone: String, code: String): String {
        // 一眼就不是手机号的先挡掉：服务端对无效号段（123 开头这种）发不出短信，
        // 索性连验证码都不校验 —— 瞎编一个码就能登进一个空壳账号。
        // 官方 App 也没拦（RegexUtil.checkPhone 比这个还松）。
        if (!phone.matches(Regex("^1(3\\d|4[5-9]|5[0-35-9]|6[2567]|7[0-8]|8\\d|9\\d)\\d{8}$"))) return "badphone"
        val r = Net.post(
            "/user/reg",
            mapOf("phone" to phone, "verify" to code, "channel" to "android_app")
        ) ?: return "fail"
        val data = r.optJSONObject("data") ?: return "fail"
        val tok = data.optString("token").orEmpty()
        if (tok.isEmpty()) return "fail"
        if (data.optBoolean("newRegister", false)) return "new"
        Net.token = tok
        Net.phone = phone
        return "ok"
    }

    fun userInfo(): UserInfo? {
        val d = Net.post("/user/info", emptyMap())?.optJSONObject("data") ?: return null
        return UserInfo(
            nickname = d.optString("userName").ifBlank { "未命名" },
            phone = d.optString("phone"),
            balance = d.optString("balance", "0.00"),
        )
    }

    /* ---------- 扫码 / 设备 ---------- */

    /**
     * 扫到的编号 → 设备。
     * 二维码里的参数名可能是 SN 也可能是 IMEI（老机器/新机器不一样），两个都试一遍。
     */
    fun scan(raw: String): Device? {
        for (key in listOf("SN", "IMEI")) {
            val d = Net.post("/goods/scan/v2", mapOf(key to raw))?.optJSONObject("data") ?: continue
            val goodsId = d.optString("id")
            if (goodsId.isBlank()) continue
            return Device(
                goodsId = goodsId,
                name = "",
                shopName = "",
                imei = raw,
                sn = raw,
                categoryName = d.optString("categoryName", "饮水机"),
                shopId = d.optString("shopId"),
            )
        }
        return null
    }

    /** 补齐设备详情(名称 / imei / machineId / 单价)。 */
    fun fillDetail(dev: Device): Device {
        val d = Net.post("/goods/normal/details", mapOf("goodsId" to dev.goodsId))
            ?.optJSONObject("data") ?: return dev
        val sku = Net.post("/goods/normal/skus", mapOf("goodsId" to dev.goodsId))
            ?.optJSONArray("data")?.optJSONObject(0)

        // 真正的单价藏在 extAttr.level[0].price 里(skus.price 只是标价 "1.00")
        val realPrice = try {
            val ext = sku?.optString("extAttr").orEmpty()
            if (ext.isBlank()) "" else {
                val lv = JSONObject(ext).optJSONArray("level")
                lv?.optJSONObject(0)?.optString("price").orEmpty()
            }
        } catch (_: Exception) { "" }

        return dev.copy(
            name = d.optString("name"),
            shopName = d.optString("orgName"),
            imei = d.optString("imei").ifBlank { dev.imei },
            machineId = d.optString("machineId"),
            shopId = d.optString("shopId", dev.shopId),
            skuId = sku?.optString("skuId").orEmpty(),
            price = realPrice.ifBlank { sku?.optString("price").orEmpty() },
            online = d.optInt("soldState", 1) == 1,
        )
    }

    /** 设备自检:health=true 表示机器可用。 */
    fun deviceHealthy(goodsId: String): Boolean {
        val d = Net.post("/trade/check_device_status", mapOf("goodsId" to goodsId))
            ?.optJSONObject("data") ?: return false
        return d.optBoolean("health", false)
    }

    /**
     * 机器现在到底空不空。
     *
     * 返回 workStatus：1=空闲、2=我自己的订单在跑、3=别人在用；0=没问到。
     * 枚举来自官方 App v1.96.1 的反编（记录在 ~/projects/reverse/FLOWS_API.md），
     * 项目这边的 docs/API.md 也抄了一份。
     */
    fun workStatus(skuId: String): Int {
        if (skuId.isBlank()) return 0
        val d = Net.post("/goods/water/sync", mapOf("skuId" to skuId))
            ?.optJSONObject("data") ?: return 0
        return d.optInt("workStatus", 0)
    }

    /**
     * 结束接水 —— 告诉服务端这次用水完了。
     *
     * 官方流程（docs/API.md 第 7 步）里这一步是必须的：不调它，机器那头会一直
     * 保持解锁态（workStatus 卡在 2），界面上的锁回去了也没用。
     * 它不解锁也不出水，只是收尾 —— 不属于「绝不在自动流程里解锁」那条约束。
     */
    fun endWater(goodsId: String): Boolean {
        if (goodsId.isBlank()) return false
        val r = Net.post("/goods/endWater", mapOf("goodsId" to goodsId)) ?: return false
        return r.optBoolean("data", false)
    }

    /* ---------- 订单 ---------- */

    /**
     * 拉一页订单。
     *
     * 失败时返回 null（不是空列表）—— 调用方需要区分「没有下一页」和「这页没拉下来」。
     * 返回空列表会把网络故障伪装成「拉完了」，静默截断数据：
     * 实测漏掉第 6 页的 93 条，整个 2024 年（91 单）从统计里消失，
     * 总额从 ¥223 变成 ¥170，而且没有任何提示。
     */
    fun orders(page: Int = 1, size: Int = 50): List<Order>? {
        val d = Net.post(
            "/order/list",
            mapOf("page" to "$page", "pageSize" to "$size", "orderStatus" to "")
        )?.optJSONObject("data") ?: return null
        val arr = d.optJSONArray("items") ?: return null
        val out = ArrayList<Order>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += Order(
                orderNo = o.optString("orderNo"),
                machineName = o.optString("machineName"),
                payPrice = o.optString("payPrice", "0.00"),
                createTime = o.optString("createTime"),
                orderStatus = o.optInt("orderStatus", 0),
            )
        }
        return out
    }

    /* ---------- 汇总 ---------- */

    data class Summary(
        val total: Double,
        val count: Int,
        val max: Double,
        val avg: Double,
        val byDay: List<Pair<String, Double>>,
        val topMachine: String,
    )

    fun summarize(list: List<Order>): Summary {
        if (list.isEmpty()) return Summary(0.0, 0, 0.0, 0.0, emptyList(), "—")
        val prices = list.mapNotNull { it.payPrice.toDoubleOrNull() }
        val byDay = list.groupBy { it.createTime.take(10) }
            .map { (d, os) -> d to os.sumOf { it.payPrice.toDoubleOrNull() ?: 0.0 } }
            .sortedBy { it.first }
        val top = list.groupingBy { it.machineName }.eachCount().maxByOrNull { it.value }?.key ?: "—"
        return Summary(
            total = prices.sum(),
            count = list.size,
            max = prices.maxOrNull() ?: 0.0,
            avg = if (prices.isEmpty()) 0.0 else prices.sum() / prices.size,
            byDay = byDay,
            topMachine = top,
        )
    }

    /** 抄自抓包:promotions 是固定的三个空占位 */
    private const val PROMOTIONS =
        "[{\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-6\"}," +
            "{\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-7\"}," +
            "{\"assetId\":\"0\",\"oldPromotionId\":\"0\",\"orgId\":\"0\",\"promotionId\":\"0\",\"promotionType\":\"-8\"}]"
}
