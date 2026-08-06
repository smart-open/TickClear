package com.tickclear.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.tickclear.app.domain.log.AppLogger

/**
 * 轻量震动工具：打卡/完成时给触觉反馈（零新依赖）。
 * [VIBRATE] 为普通权限，已在 AndroidManifest 声明，无需运行时申请。
 * minSdk=26 → O+ 路径使用 VibrationEffect.createOneShot（API 26 原生）；
 * 低版本分支仅作兜底（理论上不可达），以 Build.VERSION 守卫满足 lint NewApi。
 */
object Haptic {
    private const val TAG = "Haptic"

    fun vibrate(context: Context, millis: Long = 60, amplitude: Int = 200) {
        runCatching {
            val vib = ContextCompat.getSystemService(context, Vibrator::class.java) ?: return
            @Suppress("DEPRECATION")
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ 预定义「重击」效果：设备调校过的强震动，体验更一致。
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                // API 26+ 使用指定振幅的一次性震动；amplitude 用 200/255 保证大多数机型可感知。
                vib.vibrate(VibrationEffect.createOneShot(millis, amplitude.coerceIn(1, 255)))
            }
        }.onFailure { AppLogger.d(TAG, "vibrate skipped: ${it.message}") }
    }
}
