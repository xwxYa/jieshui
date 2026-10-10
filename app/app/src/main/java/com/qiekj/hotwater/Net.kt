package com.qiekj.hotwater

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 网络层 —— 零依赖,只用 HttpURLConnection。
 *
 * 签名算法逆向自 https://github.com/Night-stars-1/wash 的 SigningInterceptor,
 * 已在本机验证可用(登录 / 设备 / 订单 / 设备自检 全部 200)。
 *
 * ⚠️ 安全边界:本文件只提供 GET、查询类 POST。
 *    出水(unlock)、绑支付、下单 —— 一律不在这个 App 里实现。
 */
object Net {

    private const val BASE = "https://userapi.qiekj.com"
    private const val APP_SECRET = "nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o="
    private const val CHANNEL = "android_app"

    /** 取自备用机上官方 App 的实际版本号(1.96.1 / versionCode 174) */
    const val VERSION = "1.96.1"

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("hotwater", Context.MODE_PRIVATE)
    }

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    var phone: String
        get() = prefs.getString("phone", "") ?: ""
        set(v) = prefs.edit().putString("phone", v).apply()

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * 服务端最近一次回的 code（-1 = 请求压根没到服务端，比如超时 / 断网）。
     *
     * 存在的理由：调用方得能区分「服务端明确拒绝」和「网络抖了一下」。
     * token 失效时服务端回 code=2「未登录」；超时也拿不到 data，
     * 但这两件事的处理方式天差地别 —— 前者该清 token，后者绝不能清。
     *
     * 线程私有（ThreadLocal）：桥调用是并发的（hasToken 和 orders 可能同时在跑），
     * 而这是个「最近一次请求」的结果，全局共享就会被别的请求覆盖。
     * 实测踩过：hasToken 刚写成 2，正在翻页的 orders 下一轮读到 2，
     * 以为 token 失效直接 break —— 593 单只拉到 500，
     * 2024 年整年从统计里消失，总额 ¥223 变成 ¥170，且不报错。
     *
     * 线程池会复用线程，但 Net.post 每次都会重写这个值，
     * 所以不会读到上一个请求的残留。
     */
    private val codeTL = ThreadLocal.withInitial { -1 }
    var lastCode: Int
        get() = codeTL.get()
        private set(v) { codeTL.set(v) }

    /** 同步执行一次 POST(form-urlencoded),返回解析后的 JSON;失败返回 null。 */
    fun post(path: String, form: Map<String, String>): JSONObject? {
        val t = token
        val ts = System.currentTimeMillis().toString()
        val raw = "appSecret=$APP_SECRET&channel=$CHANNEL&timestamp=$ts&token=$t&version=$VERSION&$path"
        val body = (form + mapOf("token" to t))
            .entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", t)
                setRequestProperty("Version", VERSION)
                setRequestProperty("channel", CHANNEL)
                setRequestProperty("timestamp", ts)
                setRequestProperty("sign", sha256(raw))
                setRequestProperty("User-Agent", "okhttp/4.12.0")
                // 官方 App 每个请求都带这个（HeadInterceptor 里的 BrandUtils.getDeviceBrand）
                setRequestProperty("phoneBrand", android.os.Build.BRAND)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            // 出问题时 adb logcat -s APPNET 就能看到到底卡在哪一步
            Log.i("APPNET", "POST $path → $code, ${text.length} 字节")
            JSONObject(text).also { lastCode = it.optInt("code", -1) }
        } catch (e: Exception) {
            Log.e("APPNET", "POST $path 挂了", e)
            lastCode = -1                       // 没碰到服务端
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun get(path: String): JSONObject? {
        val t = token
        val ts = System.currentTimeMillis().toString()
        val raw = "appSecret=$APP_SECRET&channel=$CHANNEL&timestamp=$ts&token=$t&version=$VERSION&$path"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Authorization", t)
                setRequestProperty("Version", VERSION)
                setRequestProperty("channel", CHANNEL)
                setRequestProperty("timestamp", ts)
                setRequestProperty("sign", sha256(raw))
                setRequestProperty("User-Agent", "okhttp/4.12.0")
            }
            val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            JSONObject(text).also { lastCode = it.optInt("code", -1) }
        } catch (e: Exception) {
            lastCode = -1
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
