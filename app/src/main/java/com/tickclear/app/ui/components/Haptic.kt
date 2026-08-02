package com.tickclear.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.tickclear.app.domain.log.AppLogger

/**
 * 轻量震动工具：打卡/完成时给触觉反馈（零新依赖）。
 * [VIBRATE] 为普通权限，已在 AndroidManifest 声明，无需运行时申请。
 * minSdk=26 → O+ 路径使用 VibrationEffect.createOneShot（API 26 原生）；
 * 低版本分支仅作兜底（理论上不可达），以 Build.VERSION 守卫满足 lint NewApi。
 */
object Haptic {
    private const val TAG = "Haptic"

    fun vibrate(context: Context, millis: Long = 45, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        runCatching {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(millis, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(millis)
            }
        }.onFailure { AppLogger.d(TAG, "vibrate skipped: ${it.message}") }
    }
}
