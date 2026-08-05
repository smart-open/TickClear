package com.tickclear.app.domain.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat

/**
 * 振动按摩（休闲解压，零新依赖）。
 * 用系统 Vibrator 播放循环波形模式，提供多档力度/节奏。
 * [VIBRATE] 为普通权限，已在 AndroidManifest 声明，无需运行时申请。
 * minSdk=26：O+ 用 VibrationEffect.createWaveform；低版本分支以 Build.VERSION 守卫满足 lint NewApi。
 */
object MassageVibrator {
    /** 各模式振动时序：成对 [震动毫秒, 间隔毫秒]，repeat=0 从头循环。 */
    private val PATTERNS = mapOf(
        "gentle" to longArrayOf(120, 420),
        "strong" to longArrayOf(620, 120),
        "wave" to longArrayOf(160, 90, 260, 130, 360, 170),
        "rhythm" to longArrayOf(90, 70, 90, 70, 90, 320),
        "pulse" to longArrayOf(45, 130, 45, 130, 45, 420),
    )

    fun start(context: Context, mode: String) {
        runCatching {
            val vib = ContextCompat.getSystemService(context, Vibrator::class.java) ?: return
            @Suppress("DEPRECATION")
            if (!vib.hasVibrator()) return
            val pattern = PATTERNS[mode] ?: PATTERNS["gentle"]!!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        }
    }

    fun stop(context: Context) {
        runCatching {
            val vib = ContextCompat.getSystemService(context, Vibrator::class.java) ?: return
            vib.cancel()
        }
    }
}
