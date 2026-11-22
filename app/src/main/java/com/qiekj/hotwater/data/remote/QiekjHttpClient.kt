package com.qiekj.hotwater.data.remote

import com.qiekj.hotwater.data.model.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class QiekjHttpClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: () -> String,
    private val versionProvider: () -> String = { QiekjSigner.DEFAULT_VERSION },
    private val phoneBrandProvider: () -> String = { android.os.Build.BRAND ?: "android" },
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://userapi.qiekj.com"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun warmupDns() = withContext(Dispatchers.IO) {
        runCatching {
            val host = java.net.URI(baseUrl).host ?: return@runCatching
            okHttpClient.dns.lookup(host)
        }
    }

    suspend fun post(
        path: String,
        form: Map<String, String> = emptyMap(),
        requireCodeZero: Boolean = false
    ): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val token = tokenProvider()
        val version = versionProvider().ifBlank { QiekjSigner.DEFAULT_VERSION }
        val ts = timeProvider().toString()
        val sign = QiekjSigner.sign(normalizedPath, ts, token, version)

        val bodyBuilder = FormBody.Builder(Charsets.UTF_8)
        form.forEach { (k, v) -> bodyBuilder.add(k, v) }
        if (!form.containsKey("token")) {
            bodyBuilder.add("token", token)
        }

        val url = baseUrl.trimEnd('/') + normalizedPath
        val request = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .header("Authorization", token)
            .header("Version", version)
            .header("channel", QiekjSigner.CHANNEL)
            .header("timestamp", ts)
            .header("sign", sign)
            .header("User-Agent", "okhttp/4.12.0")
            .header("phoneBrand", phoneBrandProvider())
            .build()

        execute(request, requireCodeZero)
    }

    private fun execute(request: Request, requireCodeZero: Boolean): ApiResult<JSONObject> {
        return try {
            okHttpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) {
                    return ApiResult.NetworkError("服务响应为空 (${resp.code})")
                }
                val json = JSONObject(raw)
                val code = json.optInt("code", -1)
                if (code == 2) {
                    return ApiResult.AuthExpired
                }
                if (requireCodeZero && code != 0) {
                    return ApiResult.ServerError(code, json.optString("msg").ifBlank { "请求失败 ($code)" })
                }
                ApiResult.Success(json)
            }
        } catch (e: IOException) {
            ApiResult.NetworkError("网络未连接或超时", e)
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "响应解析失败", e)
        }
    }
}
