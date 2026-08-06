package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import com.tickclear.app.domain.log.AppLogger
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 动物拟声合成器（工具箱「动物拟声」，V2.9++）。
 * 用 AudioTrack 实时合成各动物音效（正弦+包络+少量噪声），零额外依赖、本地播放。
 * 这是「模拟」音效，并非真实录音，主打解压好玩。
 */
object AnimalSynth {
    private const val TAG = "AnimalSynth"
    private const val SR = 44100
    private var current: AudioTrack? = null

    /** 停止并释放当前正在播放的音轨。 */
    fun stop() {
        // 未播放状态下 stop() 会抛 IllegalStateException，属预期；但不得裸吞（AGENT.md §3 禁裸 catch），
        // 记 w 级日志以便排查「音轨未释放」这类偶发问题。
        runCatching {
            current?.stop()
            current?.release()
        }.onFailure { AppLogger.w(TAG, "AudioTrack 释放异常：${it.message}") }
        current = null
    }

    fun play(key: String) {
        stop()
        val samples = when (key) {
            "dog" -> dog()
            "cat" -> cat()
            "cow" -> cow()
            "sheep" -> sheep()
            "duck" -> duck()
            "pig" -> pig()
            "chicken" -> chicken()
            "lion" -> lion()
            "tiger" -> tiger()
            "bird" -> bird()
            "frog" -> frog()
            "horse" -> horse()
            else -> return
        }
        // 关键修复：MODE_STATIC 必须显式声明 buffer 大小（≥ 数据字节数），否则
        // build()/write() 在部分机型抛 IllegalStateException；该调用在后台协程且无
        // 异常处理器时会直接杀死进程（表现为点击动物音效闪退）。用 try/catch 兜住，
        // 合成/播放失败只静默放弃，绝不连累主流程。
        runCatching {
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            current = track
            track.play()
        }.onFailure { AppLogger.w(TAG, "音效合成/播放失败（key=$key）：${it.message}") }
    }

    // ---------- 合成基元 ----------
    private fun tone(
        durSec: Double,
        freq: (Double) -> Double,
        gain: (Double) -> Double,
        noise: Double = 0.0,
    ): ShortArray {
        val n = (durSec * SR).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val s = sin(2.0 * PI * freq(t) * t) * gain(t)
            val ns = if (noise > 0.0) (Random.nextDouble() * 2 - 1) * noise else 0.0
            val v = (s + ns).coerceIn(-1.0, 1.0)
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun silence(sec: Double): ShortArray = ShortArray((sec * SR).toInt())

    private fun concat(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(out, off)
            off += p.size
        }
        return out
    }

    /** 攻击/释放包络：t 为秒，dur 总时长，a 攻击比例，r 释放比例。 */
    private fun env(t: Double, dur: Double, a: Double, r: Double): Double {
        val x = t / dur
        return when {
            x < a -> x / a
            x > 1 - r -> maxOf(0.0, (1 - x) / r)
            else -> 1.0
        }
    }

    private fun g(dur: Double, a: Double, r: Double): (Double) -> Double = { t -> env(t, dur, a, r) }

    // ---------- 各动物 ----------
    private fun dog() = concat(
        tone(0.12, { 320.0 }, g(0.12, 0.05, 0.3)),
        silence(0.06),
        tone(0.12, { 300.0 }, g(0.12, 0.05, 0.3)),
        silence(0.06),
        tone(0.14, { 260.0 }, g(0.14, 0.05, 0.3)),
    )

    private fun cat() = tone(0.7, { t ->
        520.0 + 380.0 * sin(PI * (t / 0.7)) + 20.0 * sin(2.0 * PI * 12.0 * t)
    }, g(0.7, 0.15, 0.25))

    private fun cow() = tone(0.9, { t ->
        180.0 + 25.0 * sin(2.0 * PI * 5.0 * t)
    }, g(0.9, 0.2, 0.3))

    private fun sheep() = tone(0.5, { t ->
        420.0 + 60.0 * sin(2.0 * PI * 8.0 * t)
    }, g(0.5, 0.1, 0.2))

    private fun duck() = concat(
        tone(0.18, { 600.0 }, g(0.18, 0.02, 0.4), noise = 0.5),
        silence(0.05),
        tone(0.18, { 560.0 }, g(0.18, 0.02, 0.4), noise = 0.5),
    )

    private fun pig() = tone(0.35, { t ->
        240.0 + 80.0 * sin(2.0 * PI * 18.0 * t)
    }, g(0.35, 0.05, 0.2))

    private fun chicken() = concat(
        tone(0.08, { 1100.0 }, g(0.08, 0.01, 0.3)),
        silence(0.05),
        tone(0.08, { 1150.0 }, g(0.08, 0.01, 0.3)),
        silence(0.05),
        tone(0.08, { 1050.0 }, g(0.08, 0.01, 0.3)),
    )

    private fun lion() = tone(1.0, { t ->
        110.0 + 60.0 * sin(PI * (t / 1.0))
    }, g(1.0, 0.2, 0.3), noise = 0.3)

    private fun tiger() = tone(0.9, { t ->
        150.0 + 60.0 * sin(PI * (t / 0.9))
    }, g(0.9, 0.2, 0.3), noise = 0.3)

    private fun bird() = concat(
        tone(0.15, { t -> 2200.0 + (t / 0.15) * 1000.0 }, g(0.15, 0.02, 0.3)),
        silence(0.06),
        tone(0.15, { t -> 2400.0 + (t / 0.15) * 1000.0 }, g(0.15, 0.02, 0.3)),
        silence(0.06),
        tone(0.15, { t -> 2600.0 + (t / 0.15) * 800.0 }, g(0.15, 0.02, 0.3)),
    )

    private fun frog() = concat(
        tone(0.16, { t -> 200.0 + 40.0 * sin(2.0 * PI * 30.0 * t) }, g(0.16, 0.02, 0.4)),
        silence(0.05),
        tone(0.16, { t -> 200.0 + 40.0 * sin(2.0 * PI * 30.0 * t) }, g(0.16, 0.02, 0.4)),
        silence(0.05),
        tone(0.16, { t -> 190.0 + 40.0 * sin(2.0 * PI * 30.0 * t) }, g(0.16, 0.02, 0.4)),
    )

    private fun horse() = tone(0.6, { t ->
        550.0 + 150.0 * sin(2.0 * PI * 6.0 * t) + 80.0 * (t / 0.6)
    }, g(0.6, 0.1, 0.2))
}
