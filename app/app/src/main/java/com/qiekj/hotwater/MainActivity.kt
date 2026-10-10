package com.qiekj.hotwater

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

/**
 * 界面就是 assets/app.html —— 和设计稿是同一份东西，一个字都没重写。
 * 这里只干三件事：开满屏 WebView、把 [AndroidBridge] 挂上去、管相机权限。
 *
 * 本地网页为什么不用 file:// —— file:// 源下网页里的 getUserMedia（调相机）
 * 在很多 WebView 版本上会被拒。走 appassets.androidplatform.net 这个 https 源
 * 才是官方推荐做法，相机、localStorage 都能正常用。
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val askCam = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        web.evaluateJavascript("window.onCamReady&&window.onCamReady($granted)", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Net.init(this)
        DeviceStore.init(this)
        OrdersCache.init(this)

        // 网页远程调试口：只有 debug 包开着，release 包关掉 —— 没道理留个
        // 谁都能连进来看的窗口在自己手机上。
        WebView.setWebContentsDebuggingEnabled(
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false   // 取景框要能自动开播
            settings.textZoom = 100                             // 别跟着系统字体把版面撑坏
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = ViewGroup.OVER_SCROLL_NEVER

            // 别震。View 默认 longClickable = true，在任意位置长按都会触发系统的
            // HEAVY_CLICK 震动反馈（`adb shell dumpsys vibrator_manager` 里能看到
            // opPkg=com.qiekj.hotwater / fallback=true）。页面里根本没有长按功能，
            // 留着它只会让人以为按到了什么东西。
            isLongClickable = false
            isHapticFeedbackEnabled = false
            setBackgroundColor(0xFFFFF8F6.toInt())              // 和页面底色一致,免得启动闪白

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }

            webChromeClient = object : WebChromeClient() {
                /** 网页请求用相机。安卓的运行时权限在外面已经要过了，这里直接放行。 */
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }

                /** 网页里的报错全记到 logcat：adb logcat -s APPJS */
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    Log.i("APPJS", msg.message() + "  @" + msg.lineNumber())
                    return true
                }
            }

            addJavascriptInterface(
                AndroidBridge(
                    { ensureCameraPermission() },
                    { buzz(it) },
                    /* 桥的活干完了，从后台线程把结果丢回网页。
                       web 是 Activity 的字段，这会儿已经赋值了 —— 回调总是晚于这里执行。 */
                    { js -> runOnUiThread { web.evaluateJavascript(js, null) } }
                ),
                "Android"
            )
        }

        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/app.html")
    }

    /** 网页点扫码时先确保拿到了相机权限，结果通过 window.onCamReady 回给网页。 */
    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        runOnUiThread {
            if (granted) {
                web.evaluateJavascript("window.onCamReady&&window.onCamReady(true)", null)
            } else {
                askCam.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * 震一下。长按设备卡片时网页会叫到这里。
     *
     * 22ms / 强度 120 —— 就是「嗒」一下的手感。不更长是因为长按之后紧接着就是
     * 卡片浮起动效，震动抢戏反而乱。
     */
    /**
     * 震一下。
     *
     * ms 是时长，不传就给 22 —— 长按设备卡片时网页叫的就是无参那个手感：
     * 「啵」一下。不更长是因为长按之后紧接着就是卡片浮起动效，
     * 震动抢戏反而乱。
     *
     * 彩蛋里会传越来越大的值（12 → 44），越点越重。
     */
    private fun buzz(ms: Int = 22) {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val d = ms.coerceIn(1, 400)
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(d.toLong(), 120))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(d.toLong())
            }
        } catch (e: Exception) {
            Log.e("APPNET", "buzz 挂了", e)
        }
    }

    /** 返回键先交给网页：子页面开着就先收起子页面，否则退出。 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : 'exit'") { r ->
            if (r == null || r == "null" || r.contains("exit")) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
