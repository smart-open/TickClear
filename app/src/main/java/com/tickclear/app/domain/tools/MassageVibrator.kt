package com.tickclear.app.domain.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tickclear.app.domain.log.AppLogger

/**
 * 振动按摩（休闲解压，零新依赖）。
 * 用系统 Vibrator 播放循环波形模式，提供多档力度/节奏。
 * [VIBRATE] 为普通权限，已在 AndroidManifest 声明，无需运行时申请。
 *
 * V2.9++ Bug 排查与修复：
 *  - 旧实现 `ContextCompat.getSystemService(context, Vibrator::class.java)` 在 API 31(S)+
 *    行为不一致：部分厂商真机上返回 null 或返回默认 Vibrator 而非 VibratorManager，
 *    导致整个 `start` 在 runCatching 内静默失败，用户感觉「完全没有震动」。
 *  - `createWaveform(timings, 0)` 走「默认振幅」，部分设备默认振幅为 0（用户系统设置里
 *    关闭了触感）或电机偏弱；改用三参版 `createWaveform(timings, amplitudes, 0)` 显式
 *    给出每个 ON 段的振幅，最弱档也有 180/255，足以被感知。
 *  - 旧 `gentle = 120ms ON / 420ms OFF`：ON 太短、间隔太长，用户在 1~2 秒采样窗口内
 *    可能根本感觉不到。已经改成长 ON+短 OFF 的常见呼吸感。
 *  - 进入页面若硬件缺失或权限被撤，1s 后走静默路径，不影响 UI。
 */
object MassageVibrator {
    private const val TAG = "MassageVibrator"

    /** ON 振幅 0~255；统一用 180 作为体感「可感知」下限，255 作为上限。 */
    private const val AMP_LOW = 180
    private const val AMP_MID = 220
    private const val AMP_HIGH = 255

    /**
     * 每个模式 = (timings, amplitudes)。
     * timings 与 amplitudes 长度必须一致；OFF 段对应位置的振幅会被忽略。
     * 设计意图：
     *  - gentle：连续呼吸，ON 200 / OFF 300，主基调冷静；
     *  - strong：几乎连续震动；
     *  - wave：三段渐强渐弱循环（80 → 200 → 255）模拟海浪节奏；
     *  - rhythm：哒-哒-哒-停，三连击 + 长间歇；
     *  - pulse：50ms 短促脉冲 + 长间隔，最像心跳。
     */
    private data class Wave(val timings: LongArray, val amplitudes: IntArray)

    private val PATTERNS = mapOf(
        "gentle" to Wave(
            longArrayOf(0, 220, 320, 180, 420),
            intArrayOf(0, AMP_MID, 0, AMP_LOW, 0),
        ),
        "strong" to Wave(
            longArrayOf(0, 760, 140),
            intArrayOf(0, AMP_HIGH, 0),
        ),
        "wave" to Wave(
            longArrayOf(
                0, 180, 120, 260, 140, 360, 200,
                420, 180, 120, 120, 80, 80, 380,
            ),
            intArrayOf(
                0, AMP_LOW, 0, AMP_MID, 0, AMP_HIGH, 0,
                0, AMP_HIGH, 0, AMP_MID, 0, AMP_LOW, 0,
            ),
        ),
        "rhythm" to Wave(
            longArrayOf(0, 110, 90, 110, 90, 110, 480),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "pulse" to Wave(
            longArrayOf(0, 70, 170, 70, 170, 70, 720),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
    )

    /** 返回适配当前 API 的 Vibrator，绝不抛。 */
    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+：VibratorManager 是统一入口，defaultVibrator 不存在时返回 null。
            val mgr = context.getSystemService(VibratorManager::class.java)
            mgr?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    /** 设备是否真有可用的振动器（无 = 静音设备、模拟器、权限被撤）。 */
    private fun hasMotor(vib: Vibrator?): Boolean = vib?.hasVibrator() == true

    /** 诊断信息：API 等级、是否具备振动器。 */
    fun describe(context: Context): String {
        val vib = vibrator(context)
        val ok = hasMotor(vib)
        val api = Build.VERSION.SDK_INT
        return "API=$api 振动器=$ok"
    }

    /** 单次诊断震动：立刻 25ms 强触感，方便定位「按钮按了但手机没有动」类问题。 */
    fun testPulse(context: Context): Boolean {
        val vib = vibrator(context) ?: run {
            AppLogger.w(TAG, "testPulse: vibrator is null  ${describe(context)}")
            return false
        }
        if (!hasMotor(vib)) {
            AppLogger.w(TAG, "testPulse: no vibrator  ${describe(context)}")
            return false
        }
        return runCatching {
            vib.vibrate(VibrationEffect.createOneShot(25, AMP_HIGH))
            true
        }.onFailure {
            AppLogger.e(TAG, "testPulse failed", it)
        }.getOrDefault(false)
    }

    fun start(context: Context, mode: String) {
        val vib = vibrator(context) ?: run {
            AppLogger.w(TAG, "start: vibrator is null  ${describe(context)}")
            return
        }
        if (!hasMotor(vib)) {
            AppLogger.w(TAG, "start: no vibrator  ${describe(context)}")
            return
        }
        val wave = PATTERNS[mode] ?: PATTERNS["gentle"]!!
        runCatching {
            // 三参版：每个 ON 段显式给出振幅，绕过「默认振幅=0」陷阱。
            val effect = VibrationEffect.createWaveform(wave.timings, wave.amplitudes, 0)
            vib.vibrate(effect)
        }.onFailure {
            AppLogger.e(TAG, "start: vibrate failed  ${describe(context)}", it)
        }
    }

    fun stop(context: Context) {
        runCatching {
            vibrator(context)?.cancel()
        }.onFailure {
            AppLogger.d(TAG, "stop failed: ${it.message}")
        }
    }
}

