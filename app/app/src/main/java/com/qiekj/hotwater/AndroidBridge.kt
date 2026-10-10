package com.qiekj.hotwater

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 网页界面和原生之间的桥。网页里用 `Android.xxx()` 调用。
 *
 * 为什么要这层：网页自己去请求接口会被浏览器的跨域策略挡掉，而且签名算法在 Kotlin
 * 这边已经写好了。所以网页只负责画，网络请求和解码都从这里转发。
 *
 * ⚠️ @JavascriptInterface 的方法虽然跑在 WebView 的后台线程上，但**网页那边是同步
 *    等它返回的** —— 在这里干等 3 秒网络请求，页面就整整冻住 3 秒（点不动、动画停）。
 *    所以凡是会发网络请求的方法一律：立刻返回 → 后台线程干活 → 干完喊网页的
 *    window.onXxx(json)。网页侧那些回调写在 ui/build_app.py 里。
 */
class AndroidBridge(
    /** 请求相机权限。结果通过网页的 window.onCamReady(ok) 回来。 */
    private val onAskCamera: () -> Unit,
    /** 震一下。只给「长按设备卡片」用。 */
    private val onBuzz: (Int) -> Unit,
    /** 把一段 JS 丢回网页（外面负责切回主线程）。 */
    private val emit: (String) -> Unit,
) {

    private fun ok() = JSONObject().put("ok", true)
    private fun fail(msg: String) = JSONObject().put("ok", false).put("msg", msg)

    private val TAG = "APPJS"

    /** 订单每页拉多少。接口默认 50，这里要 100 减少往返次数。 */
    private val PAGE = 100

    /** 最多拉多少页 —— 防止异常账号把内存和流量拉爆（100 × 60 = 6000 单）。 */
    private val MAX_PAGES = 60

    /**
     * 网络请求丢到后台线程去，别占着网页在那儿干等。
     *
     * 并发的 —— 接水解锁不该因为正在拉账单就等 20 秒。
     * 以前每个调用各 new 一个 Thread，现在用缓存线程池：同样并发，
     * 但不会因为快速点几下就爆出一堆线程。
     *
     * 注意：并发的安全性靠 Net.lastCode 是线程私有的来保证，
     * 不是靠把调用排队。之前把这里改成单线程队列是为了避开 lastCode 被踩，
     * 代价是拉账单的 20 秒里所有桥调用（包括接水解锁）都得排队 —— 不能接受。
     */
    private val worker = Executors.newCachedThreadPool { r ->
        Thread(r, "bridge-net").apply { isDaemon = true }
    }

    private fun bg(block: () -> Unit) {
        worker.execute(block)
    }

    /** 活干完了喊网页一声。payload 走 JSON 转义，免得里面的引号把 JS 拆了。 */
    private fun done(fn: String, payload: String) {
        emit("window.$fn && window.$fn(" + JSONObject.quote(payload) + ")")
    }

    private fun devJson(d: Device) = JSONObject()
        .put("goodsId", d.goodsId)
        .put("name", d.name)
        .put("shopName", d.shopName)
        .put("skuId", d.skuId)
        .put("price", d.price)
        .put("machineId", d.machineId)
        .put("online", d.online)

    /* ---------- 登录 ---------- */

    /**
     * 网页拿这个判断要不要显示登录页。
     *
     * 不能只看 token 字符串非空 —— token 会过期，过期后还当成已登录，
     * 用户就会卡在一个永远空白的设备列表上，还不知道为什么。所以顺便验一下。
     */
    @JavascriptInterface
    fun hasToken() = bg {
        var alive = false
        var netFail = false
        if (Net.token.isNotEmpty()) {
            val info = Api.userInfo()
            if (info != null) {
                alive = true
            } else if (Net.lastCode == 2) {
                // 服务端明确回 code=2「未登录」→ token 是真的失效了，清掉
                Net.token = ""
            } else {
                // 超时 / 断网 / 服务端 5xx —— token 没失效，是网络没通。
                //
                // 这里必须和「token 真失效」分开报：以前两种情况都回 "0"，
                // 网页就停在登录页，用户看到登录页以为掉登录了，
                // 于是重新发验证码 —— 每日额度就是这么被撞光的，
                // 而他其实一直是登录状态，只是网络抖了一下。
                netFail = true
            }
        }
        done("onHasToken", if (alive) "1" else if (netFail) "?" else "0")
    }

    @JavascriptInterface
    fun sendCode(phone: String) = bg {
        val err = Api.sendCode(phone.trim())
        done("onSendCode", (if (err.isEmpty()) ok() else fail(err)).toString())
    }

    @JavascriptInterface
    fun login(phone: String, code: String) = bg {
        val j = when (Api.verify(phone.trim(), code.trim())) {
            "ok"       -> ok()
            "new"      -> fail("这个号没注册过 —— 登进去也是个空账号")
            "badphone" -> fail("这不像个手机号")
            else       -> fail("验证码不对")
        }
        done("onLogin", j.toString())
    }

    /** 当前登录的手机号。界面要把它显示出来 —— 登错号必须一眼看得见。 */
    @JavascriptInterface
    fun phone(): String = Net.phone

    @JavascriptInterface
    fun logout(): String {
        Net.token = ""
        // phone 故意留着：它现在扮演「上次登录的号」，登录页要拿它预填，
        // 免得每天重新输 11 位数字。登不登录看 token，不看 phone。
        return ok().toString()
    }

    /* ---------- 相机 / 扫码 ---------- */

    @JavascriptInterface
    fun askCamera() {
        onAskCamera()
    }

    /**
     * 手动震一下。
     *
     * 为什么不用网页那边的 navigator.vibrate()：WebView 里它要额外的 settings 开关，
     * 不同 ROM 表现还不一样，甚至静默不响。原生这边一行就稳。
     *
     * 只给「长按卡片进入拖动」用 —— 页面上其他地方长按应该是死的
     * （见 MainActivity 里 webView.isLongClickable = false）。
     */
    /**
     * 震一下。ms 是时长，不传给 22（长按卡片那个手感）。
     * 彩蛋里会传越来越大的值，越点越重。
     */
    @JavascriptInterface
    fun buzz(ms: Int) = onBuzz(ms)

    /**
     * 网页每 250ms 送一帧过来（已经拉过对比度的 JPEG dataURL），
     * 交给 ML Kit 解码。解不出来返回空串。
     */
    @JavascriptInterface
    fun scanFrame(dataUrl: String): String = try {
        QrDecoder.decode(dataUrl)
    } catch (e: Exception) {
        ""
    }

    /**
     * 扫码结果 → 设备。
     *
     * 二维码里是一整条 https://h5.qiekj.com/... 的网址，里面的参数名各版本还不一样，
     * 所以这里不猜格式：把网址里像设备号的都抠出来，挨个拿去问服务端，
     * 哪个能查出设备就用哪个。
     */
    @JavascriptInterface
    fun addDeviceFromCode(raw: String) = bg { done("onAddDevice", addDevice(raw)) }

    /** 手动输设备号（扫不出来时的兜底）。两个入口的失败处理不一样，所以回调也分开。 */
    @JavascriptInterface
    fun addDeviceByNo(no: String) = bg { done("onAddDeviceNo", addDevice(no)) }

    /** 把一段文本里像设备号的都抠出来，挨个问服务端，谁认得就用谁。 */
    private fun addDevice(raw: String): String {
        val cands = candidates(raw)
        if (cands.isEmpty()) return fail("看不出这是哪台机器").toString()

        var msg = "查不到这台机器"
        for (c in cands) {
            val scanned = Api.scan(c) ?: continue
            val dev = Api.fillDetail(scanned)
            if (dev.goodsId.isBlank()) continue
            if (dev.skuId.isBlank()) { msg = "这台机器没开热水规格"; continue }
            DeviceStore.add(dev)
            return ok().put("device", devJson(dev)).toString()
        }
        return fail(msg).toString()
    }

    /** 从一整条扫码文本里把可能是设备号的片段抠出来，靠前的先试。 */
    private fun candidates(raw: String): List<String> {
        val out = LinkedHashSet<String>()
        // 网址参数里的 sn / imei / code / deviceId
        Regex("(?:^|[?&#])(sn|imei|code|deviceId|deviceCode)=([A-Za-z0-9_-]{6,32})",
            RegexOption.IGNORE_CASE)
            .findAll(raw).forEach { out += it.groupValues[2] }

        val t = raw.trim()
        // 整串就是一个编号
        if (t.matches(Regex("[A-Za-z0-9_-]{6,32}"))) out += t
        // 任何一段长数字（机器号一般是 15 位）
        Regex("[0-9]{10,24}").findAll(raw).forEach { out += it.value }

        return out.toList()
    }

    @JavascriptInterface
    fun removeDevice(goodsId: String): String {
        DeviceStore.remove(goodsId)
        return ok().toString()
    }

    /**
     * 按网页给的顺序重排本地设备列表。
     *
     * 参数是 goodsId 的 JSON 数组字符串，比如 `["1000000001","1000000002"]`。
     * 排序只存在本地（服务端没有「我的设备列表」这回事，见 AGENTS.md），
     * 所以拖动排序改的就是这个列表的顺序。
     */
    @JavascriptInterface
    fun reorderDevices(ids: String): String {
        val arr = JSONArray(ids)
        DeviceStore.reorder((0 until arr.length()).map { arr.optString(it) })
        return ok().toString()
    }

    /* ---------- 数据 ---------- */

    @JavascriptInterface
    fun devices() = bg {
        val arr = JSONArray()
        DeviceStore.load().forEach { d ->
            // 顺手问一下这台机器现在空不空 —— 卡片上那行「空闲」原来是写死的
            arr.put(devJson(d).put("workStatus", Api.workStatus(d.skuId)))
        }
        done("onDevices", arr.toString())
    }

    /**
     * 拉全部订单。
     *
     * 接口是分页的，一次 100 条。攒几年下来可能几十页，全拉完要十几秒，
     * 所以先把缓存丢给网页顶着（页面立刻有东西看），再在后台把全部拉一遍。
     *
     * force = true 时跳过缓存直接拉。刚接完水必须传 true ——
     * 否则缓存没过期，新单子不会出现。
     *
     * 拉失败时不能静默给空数组 —— 网页那边「空」的意思就是「还没接过水」，
     * 会直接显示空状态。有缓存就留着缓存，没缓存才给空。
     *
     * 另外这接口不分业务：账号里别的机器（吹风机之类）的订单也会混进来。
     * 这是要的 —— 统计的是「一共花了多少」，不限于热水。
     */
    @JavascriptInterface
    fun orders(force: Boolean) = bg {
        val cached = OrdersCache.read()
        if (!force && cached != null) done("onOrders", cached)
        if (!force && cached != null && OrdersCache.fresh()) return@bg

        val all = ArrayList<Order>()
        var page = 1
        var cut = false                             // 中途拉断了？
        while (page <= MAX_PAGES) {
            val one = Api.orders(page = page, size = PAGE)
            if (Net.lastCode == 2) {
                Log.w(TAG, "orders: 第 $page 页 token 失效，已拉 ${all.size} 条")
                cut = true; break
            }
            if (one == null) {
                Log.w(TAG, "orders: 第 $page 页没拉下来（lastCode=${Net.lastCode}），已拉 ${all.size} 条")
                cut = true; break
            }
            if (one.isEmpty()) break                       // 真的没有下一页
            all += one
            Log.d(TAG, "orders: 第 $page 页 ${one.size} 条（累计 ${all.size}）")
            if (one.size < PAGE) break                     // 不够一页 = 最后一页
            page++
        }

        // 拉断了就不要写缓存 —— 半份数据比旧数据更坑：
        // 用户看到总额莫名其妙变小，还以为自己记错了。
        // 已有缓存就留着，没有才给空。
        if (cut) {
            if (cached == null) done("onOrders", "[]")
            return@bg
        }

        // 一条都没拉到：有缓存就留着，没缓存才给空
        if (all.isEmpty()) {
            if (cached == null) done("onOrders", "[]")
            return@bg
        }

        val arr = JSONArray()
        all.forEach { o ->
            val t = o.createTime                     // "2026-03-18 07:12:00"
            val day = t.take(10)                     // 2026-03-18
            val hm = if (t.length >= 16) t.substring(11, 16) else ""
            val p = day.split("-")
            val m = (p.getOrNull(1) ?: "").trimStart('0')
            val d = (p.getOrNull(2) ?: "").trimStart('0')
            arr.put(
                JSONObject()
                    .put("orderNo", o.orderNo)
                    .put("dev", o.machineName)
                    .put("amt", o.payPrice.toDoubleOrNull() ?: 0.0)
                    .put("time", hm)
                    .put("dateKey", day)
                    .put("date", m + "月" + d + "日")
            )
        }
        val json = arr.toString()
        OrdersCache.write(json)
        done("onOrders", json)
    }

    /* ---------- 解锁 / 结束 ---------- */

    /**
     * 结束接水：告诉服务端这次用水完了。
     *
     * 不调它的话，机器那头会一直保持解锁态 —— 界面上的锁是回去了，机器没锁。
     * 它不解锁也不出水，只是收尾，所以不受「绝不在自动流程里解锁」那条约束。
     */
    @JavascriptInterface
    fun endWater(goodsId: String) = bg {
        val okk = Api.endWater(goodsId)
        done("onEndWater", (if (okk) ok() else fail("没通知到机器")).toString())
    }

    /**
     * ⚠️ 解锁机器 —— **不是出水**，出水靠人在机器上按物理按键。
     * 但解锁之后那个口子就开着：谁按一下都记在这个账号上。
     * 所以任何自动流程都不许碰它，只有用户在接水页亲手按下「解锁设备」才会走到这里。
     */
    @JavascriptInterface
    fun unlock(skuId: String) = bg {
        val (orderNo, msg) = Api.unlock(skuId)
        done("onUnlock",
            if (orderNo.isNotEmpty()) ok().put("orderNo", orderNo).toString()
            else fail(msg.ifBlank { "解锁失败" }).toString())
    }
}
