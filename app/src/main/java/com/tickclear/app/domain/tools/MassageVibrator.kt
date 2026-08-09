package com.tickclear.app.domain.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tickclear.app.domain.log.AppLogger

/**
 * 振动按摩（休闲解压，零新依赖）。
 * 用系统 Vibrator 播放循环波形模式，提供多档力度/节奏，并支持多模式组合。
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
 *
 * 本次优化（放大震动）：
 *  - 振幅下限整体抬高（LOW 180→215 / MID 220→245 / HIGH 恒为 255），最弱档也明显可感。
 *  - 提高占空比（ON 更长、OFF 更短），电机转得更久 → 体感更强。
 *  - 新增无振幅控制兜底：部分机型 `hasAmplitudeControl()==false`（振幅参数被忽略，
 *    实际强度由厂商固件决定，往往偏弱）。此时把每个「开」段时长放大 [ON_BOOST] 倍，
 *    用「转更久」来补偿「振幅不可调」，避免「点了没感觉」。
 *  - 振幅在平台侧已封顶 255，无法再突破；真正的「放大」杠杆就是上面三点。
 */
object MassageVibrator {
    private const val TAG = "MassageVibrator"

    /** ON 振幅 0~255；整体抬高下限，最强档恒为 255（平台硬上限）。
     *  V2.11++ 进一步把 LOW 从 215 抬到 230：振幅不可调设备 + 默认振幅的机型
     *  即使不靠 ON_BOOST 补偿，也能听到明显触感。 */
    private const val AMP_LOW = 230
    private const val AMP_MID = 245
    private const val AMP_HIGH = 255

    /**
     * 无振幅控制设备（老机型/部分厂商）的补偿系数：
     * 把每个「开」段时长放大该倍数，用更长持续来换取更强体感。
     *  V2.11++ 从 1.7 提到 2.4：因为振幅不可调时电机本身偏弱，必须靠「拉长 ON 段」补偿。
     */
    private const val ON_BOOST = 2.4f

    /**
     * 现有模式的「放大系数」：ON 段乘该倍率（实际体感更长）、OFF 段乘 0.8 倍率（间隙更短），
     * 既保持节奏感，又让电机转得更久——同样的振幅参数下体感明显更强。
     * 仅对现有模式生效；新增模式已按「强振感」思路设计，不参与放大。
     */
    private const val ON_AMP = 1.3f
    private const val OFF_AMP = 0.8f

    /**
     * 每个模式 = (timings, amplitudes)。
     * timings 与 amplitudes 长度必须一致；OFF 段对应位置的振幅会被忽略。
     *
     * V2.11++ 扩到 15 模式：
     *  - 轻柔 / 强劲 / 波浪 / 节奏 / 脉冲 / 揉捏 / 点按 / 滚动 / 冲击 / 心跳：原有 10 个；
     *  - 颤动 / 呼吸 / 层叠 / 短促 / 深沉：新增 5 个（高密度 / 慢节奏 / 渐变 / 爆裂 / 重低频）。
     */
    private data class Wave(val timings: LongArray, val amplitudes: IntArray)

    private val PATTERNS = mapOf(
        "gentle" to Wave(
            scaledOn(longArrayOf(0, 240, 260, 200, 340)),
            intArrayOf(0, AMP_MID, 0, AMP_LOW, 0),
        ),
        "strong" to Wave(
            scaledOn(longArrayOf(0, 900, 120)),
            intArrayOf(0, AMP_HIGH, 0),
        ),
        "wave" to Wave(
            scaledOn(longArrayOf(0, 160, 110, 220, 130, 300, 150, 340, 150, 300, 130, 220, 110, 160, 300)),
            intArrayOf(
                0, AMP_LOW, 0, AMP_MID, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_MID, 0, AMP_LOW, 0, AMP_LOW, 0,
            ),
        ),
        "rhythm" to Wave(
            scaledOn(longArrayOf(0, 110, 90, 110, 90, 110, 480)),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "pulse" to Wave(
            scaledOn(longArrayOf(0, 70, 170, 70, 170, 70, 720)),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "knead" to Wave(
            scaledOn(longArrayOf(0, 120, 80, 120, 80, 120, 80)),
            intArrayOf(0, AMP_MID, 0, AMP_MID, 0, AMP_MID, 0),
        ),
        "tap" to Wave(
            scaledOn(longArrayOf(0, 60, 140, 60, 140, 60, 140)),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "roll" to Wave(
            scaledOn(longArrayOf(0, 200, 160, 320, 200, 420, 200, 320, 160)),
            intArrayOf(0, AMP_LOW, 0, AMP_MID, 0, AMP_HIGH, 0, AMP_MID, 0),
        ),
        "shock" to Wave(
            scaledOn(longArrayOf(0, 40, 60, 40, 60, 40, 300)),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "heart" to Wave(
            scaledOn(longArrayOf(0, 90, 120, 60, 400)),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        // V2.11++ 新增 5 个高强度 / 差异化模式
        "flutter" to Wave(
            // 高频微振：30ms ON / 40ms OFF × 10，模拟轻颤
            longArrayOf(0, 30, 40, 30, 40, 30, 40, 30, 40, 30, 40, 30, 40, 30, 40, 30, 40, 30, 40, 200),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, 0),
        ),
        "breath" to Wave(
            // 缓慢深呼吸：1500ms ON / 1000ms OFF 循环
            longArrayOf(0, 1500, 1000, 1500, 1500),
            intArrayOf(0, AMP_MID, 0, AMP_HIGH, 0),
        ),
        "cascade" to Wave(
            // 渐强渐弱：低→高→低，每段 100~480ms，最强段 AMP_HIGH 持续 80ms × 2
            longArrayOf(0, 100, 80, 150, 80, 220, 80, 300, 80, 380, 80, 480, 80, 380, 80, 300, 80, 220, 80, 150, 80, 100, 80, 600),
            intArrayOf(0, AMP_LOW, 0, AMP_LOW, 0, AMP_MID, 0, AMP_MID, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_MID, 0, AMP_MID, 0, AMP_LOW, 0, AMP_LOW, 0, 0),
        ),
        "burst" to Wave(
            // 5 连短促爆破 + 长间歇
            longArrayOf(0, 60, 40, 60, 40, 60, 40, 60, 40, 60, 800),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
        "deep" to Wave(
            // 重低频：600ms ON / 400ms OFF × 2，整体像被「按压」
            longArrayOf(0, 700, 400, 700, 600),
            intArrayOf(0, AMP_HIGH, 0, AMP_HIGH, 0),
        ),
    )

    /**
     * 把现有模式的 timings 按「ON/OFF」分别放大：
     *  ON 段（amp>0）×ON_AMP，OFF 段（amp=0）×OFF_AMP，使节奏更紧密、电机更持久。
     *  timings[0] 永远为 0，是延迟启动参数（createWaveform 第一个参数语义），保持不变。
     */
    private fun scaledOn(timings: LongArray): LongArray {
        if (timings.isEmpty()) return timings
        val out = LongArray(timings.size)
        out[0] = timings[0]
        // 注意：调用方传进来的 amplitudes 在 map 里独立保存，这里只能按奇偶位简化判断：
        // 我们约定 timings[0]=0 永远是延迟起点；其余奇数位是 ON、偶数位是 OFF。
        // 但当前模式的设计是 [0, ON, OFF, ON, OFF, ...]，所以 1、3、5...是 ON，2、4、6...是 OFF。
        for (i in 1 until timings.size) {
            out[i] = if (i % 2 == 1) (timings[i] * ON_AMP).toLong()
                     else (timings[i] * OFF_AMP).toLong()
        }
        return out
    }

    /** 组合时的稳定顺序，保证多模式循环序列确定、可预期。 */
    private val ORDER = listOf(
        "gentle", "strong", "wave", "rhythm", "pulse",
        "knead", "tap", "roll", "shock", "heart",
        // V2.11++ 新增 5 模式按"由柔到烈"排序，放在
        "flutter", "breath", "cascade", "burst", "deep",
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

    /** 设备是否支持「逐段振幅」控制（不支持时振幅参数被忽略，需靠时长补偿）。 */
    private fun hasAmplitudeControl(vib: Vibrator?): Boolean =
        Build.VERSION.SDK_INT >= 26 && vib?.hasAmplitudeControl() == true

    /** 诊断信息：API 等级、是否具备振动器。 */
    fun describe(context: Context): String {
        val vib = vibrator(context)
        val ok = hasMotor(vib)
        val api = Build.VERSION.SDK_INT
        return "API=$api，振动器=$ok"
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

    /**
     * 播放一组模式（可多选组合）：把所选模式的波形首尾相接拼成一个长循环，
     * 一次 vibrate 循环播放，体感上即「多种模式连续组合」。
     * [modes] 为空时直接停止（等价于关闭）。
     */
    fun start(context: Context, modes: Set<String>) {
        if (modes.isEmpty()) {
            stop(context)
            return
        }
        val vib = vibrator(context) ?: run {
            AppLogger.w(TAG, "start: vibrator is null  ${describe(context)}")
            return
        }
        if (!hasMotor(vib)) {
            AppLogger.w(TAG, "start: no vibrator  ${describe(context)}")
            return
        }
        val ampControl = hasAmplitudeControl(vib)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val (timings, amplitudes) = combine(modes, ampControl)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                vib.vibrate(effect)
            } else {
                // API < 26：无 VibrationEffect，逐段振幅不可控，仅按 timings 播放（已用 ON_BOOST 放大 ON）
                val pattern = combineLegacy(modes, ampControl)
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        }.onFailure {
            AppLogger.e(TAG, "start: vibrate failed  ${describe(context)}", it)
        }
    }

    /** 拼接所选模式的 (timings, amplitudes)；无振幅控制时拉长 ON 段补偿。 */
    private fun combine(modes: Set<String>, ampControl: Boolean): Pair<LongArray, IntArray> {
        val ts = mutableListOf<Long>()
        val as_ = mutableListOf<Int>()
        for (k in ORDER) {
            if (k !in modes) continue
            val w = PATTERNS[k] ?: continue
            for (i in w.timings.indices) {
                val on = w.amplitudes[i] > 0
                val t = if (on && !ampControl) (w.timings[i] * ON_BOOST).toLong() else w.timings[i]
                ts += t
                as_ += w.amplitudes[i]
            }
        }
        return ts.toLongArray() to as_.toIntArray()
    }

    /** API < 26 的 legacy 拼接（仅 timings，振幅不可控）。 */
    private fun combineLegacy(modes: Set<String>, ampControl: Boolean): LongArray {
        val ts = mutableListOf<Long>()
        for (k in ORDER) {
            if (k !in modes) continue
            val w = PATTERNS[k] ?: continue
            for (i in w.timings.indices) {
                val on = w.amplitudes[i] > 0
                val t = if (on && !ampControl) (w.timings[i] * ON_BOOST).toLong() else w.timings[i]
                ts += t
            }
        }
        return ts.toLongArray()
    }

    fun stop(context: Context) {
        runCatching {
            vibrator(context)?.cancel()
        }.onFailure {
            AppLogger.d(TAG, "stop failed: ${it.message}")
        }
    }
}
