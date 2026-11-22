package com.qiekj.hotwater.domain

object PhoneValidator {
    private val MAINLAND_PHONE_REGEX =
        Regex("^1(3\\d|4[5-9]|5[0-35-9]|6[2567]|7[0-8]|8\\d|9\\d)\\d{8}$")

    fun isValidMainlandPhone(phone: String): Boolean =
        MAINLAND_PHONE_REGEX.matches(phone.trim())
}

object QrParser {
    private val PARAM_REGEX = Regex(
        "(?:^|[?&#])(sn|imei|code|deviceId|deviceCode)=([A-Za-z0-9_-]{6,32})",
        RegexOption.IGNORE_CASE
    )
    private val RAW_ID_REGEX = Regex("^[A-Za-z0-9_-]{6,32}$")
    private val LONG_DIGITS_REGEX = Regex("[0-9]{10,24}")

    fun extractCandidates(raw: String): List<String> {
        val out = LinkedHashSet<String>()
        PARAM_REGEX.findAll(raw).forEach { match ->
            out += match.groupValues[2]
        }
        val trimmed = raw.trim()
        if (RAW_ID_REGEX.matches(trimmed)) {
            out += trimmed
        }
        LONG_DIGITS_REGEX.findAll(raw).forEach { match ->
            out += match.value
        }
        return out.toList()
    }
}
