package com.qiekj.hotwater.hardware.ble

sealed class BleInboundFrame {
    data object CloudDeduplicated : BleInboundFrame()
    data class EncryptedReport(val cmdType: String, val payloadHex: String) : BleInboundFrame()
    data class UpReport(val status: String, val channel: String, val payloadHex: String) : BleInboundFrame()
    data class Unknown(val rawHex: String) : BleInboundFrame()
}

/**
 * 蓝牙通知分包拼装器：支持单包完整帧与超过 20 字节的多包拼接帧。
 */
class BleFrameAssembler(private val maxBufferChars: Int = 1024) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(bytes: ByteArray): BleInboundFrame? {
        if (bytes.isEmpty()) return null
        val hex = BleProtocol.bytesToHex(bytes)
        // 优先尝试单包直接解析
        val single = BleProtocol.parseCmdBack(hex)
        if (single !is BleInboundFrame.Unknown) {
            buffer.clear()
            return single
        }
        // 否则拼入缓冲区尝试组合解析
        if (buffer.length + hex.length > maxBufferChars) {
            buffer.clear()
        }
        buffer.append(hex)
        val combined = BleProtocol.parseCmdBack(buffer.toString())
        if (combined !is BleInboundFrame.Unknown) {
            buffer.clear()
            return combined
        }
        return null
    }

    @Synchronized
    fun reset() {
        buffer.clear()
    }
}

object BleProtocol {
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "")
        if (clean.length % 2 != 0 || clean.isEmpty()) return ByteArray(0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun chunkPayload(bytes: ByteArray, chunkSize: Int = 20): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        val size = chunkSize.coerceAtLeast(1)
        val out = ArrayList<ByteArray>((bytes.size + size - 1) / size)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + size, bytes.size)
            out += bytes.copyOfRange(offset, end)
            offset = end
        }
        return out
    }

    /**
     * 解析官方 H5 app.js 里的蓝牙 CMDBACK / UP 帧结构：
     * - type == "00": 云端去重（云端已下发，蓝牙忽略）
     * - type == "03" / "07": 完整长帧取 substring(18) 作为 encryptionData 上报
     * - Up 包 (type == "03" 且长度在 11..18): status=hex[4..6], channel=hex[6..8], payload=hex.substring(10)
     */
    fun parseCmdBack(rawHex: String): BleInboundFrame {
        val hex = rawHex.trim().lowercase()
        if (hex.length < 4) return BleInboundFrame.Unknown(hex)
        return when (val type = hex.substring(2, 4)) {
            "00" -> BleInboundFrame.CloudDeduplicated
            "03", "07" -> {
                if (hex.length > 18) {
                    BleInboundFrame.EncryptedReport(type, hex.substring(18))
                } else if (hex.length > 10 && type == "03") {
                    BleInboundFrame.UpReport(
                        status = hex.substring(4, 6),
                        channel = hex.substring(6, 8),
                        payloadHex = hex.substring(10)
                    )
                } else {
                    BleInboundFrame.Unknown(hex)
                }
            }
            else -> BleInboundFrame.Unknown(hex)
        }
    }
}
