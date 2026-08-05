package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 拟声玻璃杯敲击合成器（工具箱「模拟解压」分类）。
 * 玻璃杯敲击声是高频、明亮、衰减极快的“叮”声，带非谐分音（玻璃/钟体特征）。
 * 7 个位置对应 1234567 音符（C 大调）：杯口(上)音高、杯底(下)音低。
 * 复用 AnimalSynth 的 AudioTrack 实时合成思路，零额外依赖、本地播放。
 */
object GlassSynth {
    private const val SR = 44100

    /** 1..7 对应 C5 大调音阶 do re mi fa sol la ti。 */
    private val NOTE_FREQS = floatArrayOf(
        523.25f, // 1 do
        587.33f, // 2 re
        659.25f, // 3 mi
        698.46f, // 4 fa
        783.99f, // 5 sol
        880.00f, // 6 la
        987.77f, // 7 ti
    )

    private var current: AudioTrack? = null

    /** 停止并释放当前正在播放的音轨。 */
    fun stop() {
        runCatching {
            current?.stop()
            current?.release()
        }
        current = null
    }

    /** 播放第 [note] 个位置(1..7)的玻璃杯敲击声。 */
    fun play(note: Int) {
        stop()
        val idx = (note.coerceIn(1, 7) - 1).coerceIn(0, NOTE_FREQS.lastIndex)
        val samples = glass(NOTE_FREQS[idx])
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        // MODE_STATIC 必须显式声明 buffer 大小（≥ 数据字节数），否则部分机型抛
        // IllegalStateException；runCatching 兜底，合成/播放失败只静默放弃。
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            current = track
            track.play()
        }
    }

    /**
     * 合成一次玻璃杯敲击：基频 + 两个非谐分音(2.76/5.40)，各自指数衰减，
     * 高次分音衰减更快，整体呈明亮短促的“叮”声。
     */
    private fun glass(base: Float): ShortArray {
        val dur = 0.7
        val n = (dur * SR).toInt()
        val out = ShortArray(n)
        // (频率比, 振幅, 时间常数tau：越小衰减越快)
        val partials = listOf(
            Triple(1.0, 1.0, 0.0009),
            Triple(2.76, 0.5, 0.00045),
            Triple(5.40, 0.28, 0.00028),
        )
        val norm = partials.sumOf { it.second }
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            for ((ratio, amp, tau) in partials) {
                val env = exp(-t / tau)
                s += amp * env * sin(2.0 * PI * base * ratio * t)
            }
            val atk = (t / 0.004).coerceIn(0.0, 1.0) // 极快起音
            s *= atk / norm
            out[i] = (s.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }
}
