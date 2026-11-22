package com.qiekj.hotwater.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.remote.QiekjSigner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val authPrefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "hotwater_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences("hotwater", Context.MODE_PRIVATE)
        }
    }

    private val devicePrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("devices_native", Context.MODE_PRIVATE)
    }

    var token: String
        get() = authPrefs.getString("token", "").orEmpty()
        set(value) = authPrefs.edit().putString("token", value).apply()

    var phone: String
        get() = authPrefs.getString("phone", "").orEmpty()
        set(value) = authPrefs.edit().putString("phone", value).apply()

    var apiVersion: String
        get() = authPrefs.getString("api_version", QiekjSigner.DEFAULT_VERSION)
            .orEmpty()
            .ifBlank { QiekjSigner.DEFAULT_VERSION }
        set(value) = authPrefs.edit().putString("api_version", value.trim()).apply()

    fun loadDevices(): List<Device> {
        val raw = devicePrefs.getString("list", "[]").orEmpty()
        return runCatching {
            json.decodeFromString<List<Device>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveDevices(list: List<Device>) {
        devicePrefs.edit().putString("list", json.encodeToString(list)).apply()
    }

    fun addDeviceToTop(device: Device): List<Device> {
        val updated = listOf(device) + loadDevices().filterNot { it.goodsId == device.goodsId }
        saveDevices(updated)
        return updated
    }

    fun removeDevice(goodsId: String): List<Device> {
        val updated = loadDevices().filterNot { it.goodsId == goodsId }
        saveDevices(updated)
        return updated
    }

    fun reorderDevices(orderedGoodsIds: List<String>): List<Device> {
        val all = loadDevices()
        val byId = all.associateBy { it.goodsId }
        val picked = orderedGoodsIds.mapNotNull { byId[it] }
        val pickedIds = picked.map { it.goodsId }.toSet()
        val merged = picked + all.filterNot { it.goodsId in pickedIds }
        saveDevices(merged)
        return merged
    }
}
