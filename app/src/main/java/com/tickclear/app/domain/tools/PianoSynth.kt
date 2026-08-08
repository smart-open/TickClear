package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import com.tickclear.app.domain.log.AppLogger
import kotlin.math.PI
import kotlin.math.sin
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 电子琴实时合成器（工具箱「电子琴」）。
 * 复用 AnimalSynth / FoleySynth 的 AudioTrack 思路，但改为流式（MODE_STREAM）单音引擎：
 * 按下 noteOn 即起一个写入线程持续输出正弦波，松手 noteOff 触发包络淡出；
 * 支持按住延长、快速切换音高、多键和弦（后按先响）。零额外依赖、本地播放。
 */
object PianoSynth {
    private const val TAG = "PianoSynth"
    private const val SR = 44100
    private const val CHUNK = 1024

    private val running = AtomicBoolean(false)
    private var writerThread: Thread? = null
    private var track: AudioTrack? = null

    // 当前按下的音高频率栈（后按先响，松手按栈顺序回落）
    private val pressed = ArrayDeque<Double>()
    @Volatile private var currentFreq = 440.0
    @Volatile private var gain = 0.0
    @Volatile private var targetGain = 0.0
    private var phase = 0.0

    /** 按下某音：freq 为目标频率（Hz）。 */
    @Synchronized
    fun noteOn(freq: Double) {
        pressed.addLast(freq)
        currentFreq = freq
        targetGain = 0.9
        ensureStarted()
    }

    /** 松开某音：若该音仍是当前发声音则淡出，否则回落到栈中上一个仍按住的音。 */
    @Synchronized
    fun noteOff(freq: Double) {
        while (pressed.remove(freq)) { /* 移除全部匹配 */ }
        if (pressed.isNotEmpty()) {
            currentFreq = pressed.last()
        } else {
            targetGain = 0.0
        }
    }

    /** 整体停止并释放音轨（离开页面或重置时调用）。 */
    @Synchronized
    fun stop() {
        pressed.clear()
        targetGain = 0.0
        gain = 0.0
        running.set(false)
        try { writerThread?.join(300) } catch (_: Exception) { /* 忽略 */ }
        writerThread = null
        runCatching { track?.stop(); track?.release() }
            .onFailure { AppLogger.w(TAG, "音轨释放异常：${it.message}") }
        track = null
        phase = 0.0
    }

    private fun ensureStarted() {
        if (running.get()) return
        running.set(true)
        runCatching {
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufSize = maxOf(minBuf, CHUNK * 4)
            track = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufSize)
                .build()
            track?.play()
        }.onFailure {
            AppLogger.w(TAG, "音轨初始化失败：${it.message}")
            running.set(false)
            return
        }
        writerThread = Thread({ writer() }, "PianoSynth").also { it.start() }
    }

    private fun writer() {
        val buf = ShortArray(CHUNK)
        while (running.get()) {
            // 指数式逼近目标增益，形成柔和攻击/释放包络
            val g = gain
            val ng = (g + (targetGain - g) * 0.18).coerceIn(0.0, 1.0)
            gain = ng
            if (targetGain == 0.0 && ng < 0.001) gain = 0.0
            val f = currentFreq
            for (i in 0 until CHUNK) {
                phase += f / SR
                if (phase >= 1.0) phase -= 1.0
                val s = sin(2.0 * PI * phase) * gain
                buf[i] = (s.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
            }
            val t = track
            if (t == null) { running.set(false); break }
            var writeOk = true
            runCatching { t.write(buf, 0, CHUNK) }.onFailure { writeOk = false }
            if (!writeOk) { running.set(false); break }
        }
    }
}
