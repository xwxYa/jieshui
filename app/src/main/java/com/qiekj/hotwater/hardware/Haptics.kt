package com.qiekj.hotwater.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun buzz(durationMs: Int = 22, amplitude: Int = 120) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val ms = durationMs.coerceIn(1, 400).toLong()
            val amp = amplitude.coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
        }
    }
}
