package com.qiekj.hotwater

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 二维码解码。
 *
 * 机器上的码印在蓝色背光屏上，深蓝配浅蓝，对比度很低；手机自带的扫码器和浏览器
 * 都读不出来，微信能读（微信用的是自适应阈值）。所以这里用 ML Kit —— 
 * 它比纯 JS 的解码库强得多，啃得动这种图。网页那边会先把对比度拉满再送过来。
 */
object QrDecoder {

    private val scanner by lazy {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_AZTEC, Barcode.FORMAT_PDF417)
                .build()
        )
    }

    /**
     * 解一张 dataURL 图片（`data:image/jpeg;base64,....`）。
     * 解不出来返回空串。
     *
     * ML Kit 只给异步接口，而网页那边需要一个同步的返回值，所以在这里等一下。
     * 这个方法跑在 WebView 的后台线程上，不会卡界面。
     */
    fun decode(dataUrl: String): String {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return ""
        val bytes = try {
            Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            return ""
        } ?: return ""

        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } ?: return ""

        val latch = CountDownLatch(1)
        var hit = ""
        scanner.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { list ->
                hit = list.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }.orEmpty()
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }

        latch.await(4, TimeUnit.SECONDS)
        bmp.recycle()
        return hit
    }
}
