package com.qiekj.hotwater

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 本地保存「我添加过的设备」(纯本地,不上传)。 */
object DeviceStore {

    private lateinit var ctx: Context

    fun init(c: Context) { ctx = c.applicationContext }

    private fun prefs() = ctx.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun load(): MutableList<Device> {
        val raw = prefs().getString("list", "[]") ?: "[]"
        val out = mutableListOf<Device>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Device(
                    goodsId = o.optString("goodsId"),
                    name = o.optString("name"),
                    shopName = o.optString("shopName"),
                    imei = o.optString("imei"),
                    sn = o.optString("sn"),
                    skuId = o.optString("skuId"),
                    price = o.optString("price"),
                    categoryName = o.optString("categoryName", "饮水机"),
                    machineId = o.optString("machineId"),
                    shopId = o.optString("shopId"),
                    online = o.optBoolean("online", true),
                )
            }
        } catch (_: Exception) { }
        return out
    }

    fun save(list: List<Device>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("goodsId", d.goodsId); put("name", d.name); put("shopName", d.shopName)
                put("imei", d.imei); put("sn", d.sn); put("skuId", d.skuId)
                put("price", d.price); put("categoryName", d.categoryName)
                put("machineId", d.machineId); put("shopId", d.shopId); put("online", d.online)
            })
        }
        prefs().edit().putString("list", arr.toString()).apply()
    }

    fun add(d: Device) {
        val list = load().filterNot { it.goodsId == d.goodsId }.toMutableList()
        list.add(0, d)
        save(list)
    }

    fun remove(goodsId: String) {
        save(load().filterNot { it.goodsId == goodsId })
    }

    /**
     * 按给定的 goodsId 顺序重排。
     *
     * 只认已经在库里的；名单里没提到的（理论上不该有）按原相对顺序接在后面，
     * 所以就算网页传来半截名单也不会把设备弄丢。
     */
    fun reorder(ids: List<String>) {
        val all = load()
        val byId = all.associateBy { it.goodsId }
        val picked = ids.mapNotNull { byId[it] }
        val pickedSet = picked.map { it.goodsId }.toSet()
        save(picked + all.filterNot { it.goodsId in pickedSet })
    }
}
