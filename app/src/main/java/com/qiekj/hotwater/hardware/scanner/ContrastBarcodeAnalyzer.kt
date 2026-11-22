package com.qiekj.hotwater.hardware.scanner

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ContrastEnhancer {
    /**
     * 对灰度字节数组做 6%~94% 分位自适应拉伸，专治蓝色背光饮水机低对比度二维码。
     */
    fun stretchLuma(luma: ByteArray): ByteArray {
        if (luma.isEmpty()) return luma
        val hist = IntArray(256)
        for (b in luma) {
            hist[b.toInt() and 0xFF]++
        }
        val total = luma.size
        val lowTarget = (total * 0.06f).toInt()
        val highTarget = (total * 0.94f).toInt()
        var acc = 0
        var lo = 0
        var hi = 255
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= lowTarget) {
                lo = v
                break
            }
        }
        acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= highTarget) {
                hi = v
                break
            }
        }
        val span = maxOf(hi - lo, 24)
        val lut = ByteArray(256)
        for (v in 0..255) {
            val mapped = (((v - lo) * 255) / span).coerceIn(0, 255)
            lut[v] = mapped.toByte()
        }
        val out = ByteArray(luma.size)
        for (i in luma.indices) {
            out[i] = lut[luma[i].toInt() and 0xFF]
        }
        return out
    }

    /**
     * 计算居中正方形 ROI（默认占短边 68%），确保暗背景走廊不污染中心蓝屏二维码的直方图。
     */
    fun centerSquareRoi(width: Int, height: Int, ratio: Float = 0.68f): IntArray {
        val side = (minOf(width, height) * ratio).toInt().coerceAtLeast(1)
        val left = ((width - side) / 2).coerceAtLeast(0)
        val top = ((height - side) / 2).coerceAtLeast(0)
        return intArrayOf(left, top, side, side)
    }
}

class ContrastBarcodeAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_PDF417
            )
            .build()
    )

    @Volatile
    var paused: Boolean = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (paused || !busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            busy.set(false)
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // 奇偶帧双管齐下：奇数帧做中心 ROI 对比度拉伸（专杀蓝色背光屏），偶数帧用中心原灰度（兼容普通贴纸码）
        val applyStretch = (frameCounter.getAndIncrement() % 2 == 0)
        val bitmap = runCatching { extractCenterGrayBitmap(imageProxy, applyStretch) }.getOrNull()
        imageProxy.close()

        if (bitmap == null) {
            busy.set(false)
            return
        }

        scanner.process(InputImage.fromBitmap(bitmap, rotation))
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (raw != null && !paused) {
                    paused = true
                    onDecoded(raw)
                }
            }
            .addOnFailureListener { e ->
                Log.w("HotWaterScan", "ML Kit decode failed", e)
            }
            .addOnCompleteListener {
                bitmap.recycle()
                busy.set(false)
            }
    }

    private fun extractCenterGrayBitmap(imageProxy: ImageProxy, stretch: Boolean): Bitmap {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        val roi = ContrastEnhancer.centerSquareRoi(width, height, ratio = 0.68f)
        val roiLeft = roi[0]
        val roiTop = roi[1]
        val roiSide = roi[2]

        // 中心正方形降采样到 ~540px，既锐利又极快
        val step = maxOf(1, roiSide / 540)
        val outSide = (roiSide / step).coerceAtLeast(1)
        val luma = ByteArray(outSide * outSide)
        var idx = 0
        for (y in 0 until outSide) {
            val rowOffset = (roiTop + y * step) * rowStride + roiLeft
            for (x in 0 until outSide) {
                val pos = rowOffset + x * step
                luma[idx++] = if (pos in 0 until buffer.limit()) buffer.get(pos) else 0
            }
        }

        val finalLuma = if (stretch) ContrastEnhancer.stretchLuma(luma) else luma
        val pixels = IntArray(finalLuma.size)
        for (i in finalLuma.indices) {
            val g = finalLuma[i].toInt() and 0xFF
            pixels[i] = Color.rgb(g, g, g)
        }
        return Bitmap.createBitmap(pixels, outSide, outSide, Bitmap.Config.ARGB_8888)
    }

    fun close() {
        runCatching { scanner.close() }
    }
}
