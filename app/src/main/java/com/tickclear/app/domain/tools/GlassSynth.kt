package com.tickclear.app.domain.tools

import android.content.Context
import com.tickclear.app.R
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 拟声玻璃杯敲击合成器（工具箱「模拟解压」分类）。
 * 7 个玻璃杯对应 G 大调音阶 1234567（G4..F#5）：杯中水越多音越低。
 *
 * 声音来源：优先播放程序生成的玻璃“叮”声素材（res/raw/glass_note_1..7，G 大调），
 * 缺失或播放失败时回退到本地 AudioTrack 合成的玻璃“叮”声。
 * 复用 AnimalSynth / FoleySynth 的「真实录音优先、合成兜底」思路，零额外依赖。
 */
object GlassSynth {
    private const val SR = 44100

    /** 1..7 对应 G 大调音阶 do re mi fa sol la ti（G4..F#5）。 */
    private val NOTE_FREQS = floatArrayOf(
        392.00f, // 1 do  G4
        440.00f, // 2 re  A4
        493.88f, // 3 mi  B4
        523.25f, // 4 fa  C5
        587.33f, // 5 sol D5
        659.25f, // 6 la  E5
        739.99f, // 7 ti  F#5
    )

    /** 程序生成的玻璃“叮”声素材：note 1..7 → G4..F#5。 */
    private val NOTE_RES = intArrayOf(
        R.raw.glass_note_1, R.raw.glass_note_2, R.raw.glass_note_3, R.raw.glass_note_4,
        R.raw.glass_note_5, R.raw.glass_note_6, R.raw.glass_note_7,
    )

    private var currentMp: MediaPlayer? = null
    private var currentTrack: AudioTrack? = null

    /** 释放所有正在播放的音频（离开页面时调用）。 */
    fun stop() {
        releaseMp()
        runCatching { currentTrack?.stop(); currentTrack?.release() }
        currentTrack = null
    }

    /**
     * 播放第 [note] 个玻璃杯(1..7)的敲击音。
     * 优先用真实钢琴单音素材（MediaPlayer），缺失/失败再回退到合成的“叮”声。
     */
    fun play(context: Context, note: Int) {
        val idx = (note.coerceIn(1, 7) - 1).coerceIn(0, NOTE_RES.lastIndex)
        val resId = NOTE_RES[idx]
        val mp = runCatching { MediaPlayer.create(context, resId) }.getOrNull()
        if (mp != null) {
            releaseMp()
            mp.setOnCompletionListener { releaseMp() }
            mp.setOnErrorListener { _, _, _ -> releaseMp(); true }
            currentMp = mp
            runCatching { mp.start() }
            return
        }
        // 回退：合成玻璃“叮”声
        releaseMp()
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
            currentTrack = track
            track.play()
        }
    }

    private fun releaseMp() {
        runCatching { currentMp?.stop(); currentMp?.release() }
        currentMp = null
    }

    /**
     * 合成一次玻璃杯敲击：基频 + 两个非谐分音(2.76/5.40)，各自指数衰减，
     * 高次分音衰减更快，整体呈明亮短促的“叮”声（仅在真实素材缺失时兜底使用）。
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
