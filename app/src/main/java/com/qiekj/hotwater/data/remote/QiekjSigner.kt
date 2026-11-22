package com.qiekj.hotwater.data.remote

import java.security.MessageDigest

object QiekjSigner {
    const val APP_SECRET = "nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o="
    const val CHANNEL = "android_app"
    const val DEFAULT_VERSION = "1.96.1"

    fun buildSignPayload(
        path: String,
        timestampMs: String,
        token: String,
        version: String = DEFAULT_VERSION,
        channel: String = CHANNEL,
        appSecret: String = APP_SECRET
    ): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "appSecret=$appSecret&channel=$channel&timestamp=$timestampMs&token=$token&version=$version&$normalizedPath"
    }

    fun sign(
        path: String,
        timestampMs: String,
        token: String,
        version: String = DEFAULT_VERSION
    ): String = sha256(buildSignPayload(path, timestampMs, token, version))

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
