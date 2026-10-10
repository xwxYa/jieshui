package com.qiekj.hotwater

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 订单列表的本地缓存。
 *
 * 为什么要它：`/order/list` 是分页的，拉全部要几十次请求、十几秒。
 * 每次都现拉的话，进订单页要干等；网络一抖还什么都看不到。
 * 所以拉完存一份，下次先拿它顶着，后台再刷新。
 *
 * 存文件不存 SharedPreferences —— 订单攒多了这份 JSON 有几百 KB，
 * SharedPreferences 是 XML，读一次要把整个文件解析一遍，太慢。
 */
object OrdersCache {

    private lateinit var dir: File

    fun init(c: Context) { dir = c.applicationContext.filesDir }

    private fun f() = File(dir, "orders.json")

    /** 超过这个时长就认为该刷新了。但过期也照给，只是会触发后台拉取。 */
    private const val TTL = 6 * 3600_000L

    /** 读缓存。没有、太短、或内容不是合法 JSON 都返回 null，调用方当成「没缓存」处理。 */
    fun read(): String? {
        return try {
            val file = f()
            if (!file.exists() || file.length() <= 2) return null
            val s = file.readText()
            /* 验证一下真的是 JSON 数组再交出去。
               上一次写入写到一半被杀（或者磁盘满了）会留下截断的文件，
               光看长度查不出来 —— 交到网页那边 JSON.parse 一炸，
               订单页就空白了，而且报错信息跟缓存八竿子打不着。 */
            if (s.firstOrNull() != '[' || JSONArray(s).length() < 0) return null
            s
        } catch (_: Exception) {
            null
        }
    }

    /** 缓存还新鲜吗。新鲜就不用再拉一遍。 */
    fun fresh(): Boolean = try {
        val file = f()
        file.exists() && System.currentTimeMillis() - file.lastModified() < TTL
    } catch (_: Exception) {
        false
    }

    /**
     * 写缓存。
     *
     * 先写临时文件再 rename —— rename 在同一文件系统上是原子的，
     * 要么是完整的旧内容，要么是完整的新内容，不会留下写了一半的文件。
     * 直接 writeText 覆盖的话，中途被杀就会把好缓存也毁掉。
     */
    fun write(s: String) {
        try {
            val tmp = File(dir, "orders.json.tmp")
            tmp.writeText(s)
            if (!tmp.renameTo(f())) {
                /* rename 失败（某些文件系统不支持覆盖式 rename）就退而求其次 */
                f().writeText(s)
                tmp.delete()
            }
        } catch (_: Exception) {
            /* 写不进去就算了 —— 缓存只是加速，不该因为它报错 */
        }
    }
}
