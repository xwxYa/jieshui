package com.qiekj.hotwater.hardware.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 纯 CameraX 扫码硬件控制器：封装绑定、1.4x 轻变焦、-0.85 EV 抗蓝屏晕光曝光、
 * AF/AE 中心/点按测光对焦、手电筒切换与生命周期彻底释放，与 Compose UI 完全解耦。
 */
class CameraScanController(onDecoded: (String) -> Unit) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analyzer = ContrastBarcodeAnalyzer(onDecoded = onDecoded)

    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var provider: ProcessCameraProvider? = null
    private var torchEnabled: Boolean = false

    val isBound: Boolean
        get() = camera != null

    fun setPaused(paused: Boolean) {
        analyzer.paused = paused
    }

    fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        targetPreview: PreviewView,
        onReady: () -> Unit = {}
    ) {
        previewView = targetPreview
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = targetPreview.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                p.unbindAll()
                val bound = p.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                camera = bound
                applyDefaultZoom(bound)
                applyAntiGlareExposure(bound, enableReduction = !torchEnabled)
                onReady()
            }.onFailure { e ->
                Log.e("HotWaterScan", "CameraX bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun focusCenter() {
        val pv = previewView ?: return
        if (pv.width > 0 && pv.height > 0) {
            focusAt(pv.width / 2f, pv.height / 2f)
        }
    }

    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val pv = previewView ?: return
        if (pv.width <= 0 || pv.height <= 0) return
        runCatching {
            val point = pv.meteringPointFactory.createPoint(x, y, 0.22f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        }
    }

    fun toggleTorch(): Boolean {
        torchEnabled = !torchEnabled
        camera?.let { cam ->
            cam.cameraControl.enableTorch(torchEnabled)
            applyAntiGlareExposure(cam, enableReduction = !torchEnabled)
        }
        return torchEnabled
    }

    fun unbind() {
        runCatching {
            if (torchEnabled) {
                camera?.cameraControl?.enableTorch(false)
                torchEnabled = false
            }
            provider?.unbindAll()
            camera = null
            previewView = null
        }
    }

    fun release() {
        unbind()
        analyzer.close()
        executor.shutdown()
    }

    private fun applyDefaultZoom(cam: Camera) {
        val z = cam.cameraInfo.zoomState.value ?: return
        val target = 1.4f.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        cam.cameraControl.setZoomRatio(target)
    }

    private fun applyAntiGlareExposure(cam: Camera, enableReduction: Boolean) {
        runCatching {
            val state = cam.cameraInfo.exposureState
            if (!state.isExposureCompensationSupported) return
            val step = state.exposureCompensationStep.toFloat()
            val targetIdx = if (enableReduction && step > 0f) {
                (-0.85f / step).roundToInt().coerceIn(
                    state.exposureCompensationRange.lower,
                    state.exposureCompensationRange.upper
                )
            } else {
                0.coerceIn(state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
            }
            cam.cameraControl.setExposureCompensationIndex(targetIdx)
        }
    }
}
