package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 吹笛子合成器（工具箱「模拟解压」分类）。
 *
 * 采用 AudioTrack [AudioTrack.MODE_STREAM] 持续合成：一条独立合成线程循环生成样本并
 * `write` 进音轨，频率（音高）与气息强度（音量/气声比例）由主线程实时下发，互不阻塞。
 *
 * 笛声音色近似：基频 + 极弱的二次谐波，叠加随气息增强的「气声」白噪声；起音/收声用
 * 振幅包络平滑过渡，避免爆音。音高切换走相位累加器，故换音无相位跳变、无咔哒声。
 * 纯本地合成，零额外依赖、零外部模型。
 */
object FluteSynth {
    private const val SR = 44100

    private val lock = Any()
    private var freqHz = 523.25f // 默认 do(C5)
    private var intensity = 0f // 目标气息强度 0..1（0=静默）
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    /** 设置当前音高（Hz）。相位累加器保证换音平滑。 */
    fun setNote(freq: Float) {
        synchronized(lock) { freqHz = freq.coerceAtLeast(20f) }
    }

    /** 设置目标气息强度 0..1；0 表示不吹（静默）。 */
    fun setIntensity(level: Float) {
        synchronized(lock) { intensity = level.coerceIn(0f, 1f) }
    }

    /** 是否已处于吹奏（合成线程运行中）。 */
    fun isRunning(): Boolean = running

    /** 启动持续合成。已在运行则忽略。 */
    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        // 流式音轨需要足够大的缓冲区避免欠载；取系统下限与 ~100ms 的较大者。
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, SR / 10)
        runCatching {
            val t = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufSize * 2)
                .build()
            t.play()
            track = t
            thread = Thread({ runSynthesis(t, bufSize) }, "FluteSynth").apply { start() }
        }.onFailure {
            // 初始化失败：回退到未运行状态，避免悬挂线程/音轨。
            synchronized(lock) { running = false }
            runCatching { track?.release() }
            track = null
        }
    }

    private fun runSynthesis(t: AudioTrack, bufSize: Int) {
        val buf = ShortArray(bufSize)
        var phase = 0.0
        var amp = 0.0 // 平滑后的实际振幅
        val dt = 2.0 * PI / SR
        val rand = Random(System.nanoTime())
        while (running && !Thread.interrupted()) {
            val f: Float
            val target: Float
            synchronized(lock) {
                f = freqHz
                target = intensity
            }
            // 振幅包络：每样本朝目标缓动，消除起/收声爆音。
            amp += (target - amp) * 0.06
            val breath = amp.coerceIn(0.0, 1.0)
            for (i in 0 until buf.size) {
                phase += dt * f
                if (phase > 2 * PI) phase -= 2 * PI
                val tone = sin(phase) + 0.12 * sin(2.0 * phase)
                val noise = rand.nextFloat() * 2.0 - 1.0
                // 气息越足，气声噪声占比越高，音色越「实」；同时整体被 amp 调制。
                val s = (tone * (1.0 - breath * 0.4) + noise * breath * 0.22) * amp
                buf[i] = (s.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
            }
            runCatching { t.write(buf, 0, buf.size) }
        }
    }

    /** 停止合成并释放音轨与线程。 */
    fun stop() {
        synchronized(lock) {
            if (!running) return
            running = false
            intensity = 0f
        }
        thread?.interrupt()
        thread = null
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
    }
}
