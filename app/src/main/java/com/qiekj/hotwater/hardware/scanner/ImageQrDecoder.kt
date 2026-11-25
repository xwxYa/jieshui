package com.qiekj.hotwater.hardware.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * 从相册图片 Uri 识别二维码：先直接喂 ML Kit，未命中时再做自适应对比度拉伸兜底。
 */
object ImageQrDecoder {

    private val scannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128)
        .build()

    suspend fun decodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmap(context, uri, maxSide = 1600) ?: return@runCatching null
            val scanner = BarcodeScanning.getClient(scannerOptions)
            try {
                val direct = scanBitmap(scanner, bitmap)
                if (!direct.isNullOrBlank()) return@runCatching direct

                val enhanced = buildStretchedBitmap(bitmap)
                try {
                    scanBitmap(scanner, enhanced)
                } finally {
                    if (enhanced !== bitmap) enhanced.recycle()
                }
            } finally {
                scanner.close()
                bitmap.recycle()
            }
        }.getOrNull()
    }

    private suspend fun scanBitmap(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        bitmap: Bitmap
    ): String? = suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { list ->
                val raw = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                if (cont.isActive) cont.resume(raw)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun buildStretchedBitmap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }
        val stretched = ContrastEnhancer.stretchLuma(luma)
        for (i in pixels.indices) {
            val y = stretched[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
