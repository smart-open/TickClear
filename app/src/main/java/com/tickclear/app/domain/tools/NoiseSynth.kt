package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 睡眠白噪音合成器（零新依赖、本地循环、无需联网）。
 * 三种场景均为程序化生成的循环噪声：
 *  - rain   雨声：低通滤白噪声形成柔和水声 + 偶发水滴；
 *  - stream 溪流：带通感的潺潺声 + 缓慢起伏；
 *  - cafe   咖啡馆：棕噪声低频隆隆 + 极淡中频人声感。
 *
 * 多音轨混音（V2.9++）：每个场景是一条独立的循环 AudioTrack，由系统混音器叠加，
 * 互不干扰；每条轨道各自保存音量，开关/调音即时生效，无需手工样本混叠。
 * 使用 AudioTrack MODE_STATIC 整段循环；全程 runCatching 包裹，
 * 并显式 setBufferSizeInBytes 避免 MODE_STATIC 下 write() 抛 IllegalStateException。
 */
object NoiseSynth {
    private const val SR = 22050
    private val cache = mutableMapOf<String, ShortArray>()
    private val active = LinkedHashMap<String, AudioTrack>()

    /** 取某场景的整段 16bit 样本（首次生成后缓存，保证同一场景音色稳定不抖动）。 */
    private fun baseSamples(key: String): ShortArray = cache.getOrPut(key) {
        when (key) {
            "rain" -> rain()
            "stream" -> stream()
            "cafe" -> cafe()
            "waves" -> waves()
            "wind" -> wind()
            "fire" -> fire()
            "white" -> white()
            "pink" -> pink()
            "fan" -> fan()
            else -> rain()
        }
    }

    fun isLayerActive(key: String): Boolean =
        active[key]?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun activeLayerCount(): Int = active.size

    /** 加入/恢复某场景轨道并以 [volume] 播放；已存在则仅更新音量。 */
    fun playLayer(key: String, volume: Float) {
        if (isLayerActive(key)) {
            active[key]?.setVolume(volume.coerceIn(0f, 1f))
            return
        }
        val samples = baseSamples(key)
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            // 整段无缝循环（帧索引 = 单声道样本数）
            if (track.setLoopPoints(0, samples.size, -1) == 0) {
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                active[key] = track
            } else {
                track.release()
            }
        }
    }

    /** 实时调整某场景轨道音量（不影响其它轨道）。 */
    fun setLayerVolume(key: String, volume: Float) {
        runCatching { active[key]?.setVolume(volume.coerceIn(0f, 1f)) }
    }

    /** 移除并停止某场景轨道。 */
    fun stopLayer(key: String) {
        runCatching {
            active[key]?.apply {
                stop()
                release()
            }
        }
        active.remove(key)
    }

    /** 停止并释放全部轨道。 */
    fun stopAll() {
        active.keys.toList().forEach { stopLayer(it) }
    }

    // ---------- 噪声合成基元 ----------
    /** 生成 [durSec] 秒单声道 16bit 样本；[fill] 内可闭包捕获跨样本状态。 */
    private fun buffer(durSec: Double, fill: (i: Int, t: Double, white: Double) -> Double): ShortArray {
        val n = (durSec * SR).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val w = Random.nextDouble() * 2 - 1
            val v = fill(i, t, w).coerceIn(-1.0, 1.0)
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun rain(): ShortArray {
        val lp = DoubleArray(1) { 0.0 }
        return buffer(3.0) { _, _, w ->
            lp[0] = lp[0] * 0.96 + w * 0.04 // 一阶低通 → 柔和水声
            val drop = if (Random.nextDouble() < 0.0009) Random.nextDouble() * 0.5 else 0.0
            lp[0] * 0.85 + drop
        }
    }

    private fun stream(): ShortArray {
        val s = DoubleArray(2) { 0.0 }
        return buffer(3.0) { _, t, w ->
            s[0] = s[0] * 0.9 + w * 0.1
            s[1] = s[1] * 0.6 + (w - s[0]) * 0.4 // 近似带通 → 潺潺感
            val wob = 0.7 + 0.3 * sin(2.0 * PI * 0.5 * t) // 缓慢起伏
            s[1] * 0.95 * wob
        }
    }

    private fun cafe(): ShortArray {
        val brown = DoubleArray(1) { 0.0 }
        return buffer(3.0) { _, t, w ->
            brown[0] = (brown[0] + 0.02 * w).coerceIn(-1.0, 1.0) * 0.995 // 棕噪声低频
            val mid = sin(2.0 * PI * 180.0 * t) * 0.04 * (0.5 + 0.5 * sin(2.0 * PI * 0.3 * t))
            brown[0] * 0.8 + mid
        }
    }

    /** 海浪：柔和水声 + 缓慢涨落包络，模拟一波波涌动。 */
    private fun waves(): ShortArray {
        val lp = DoubleArray(1) { 0.0 }
        return buffer(4.0) { _, t, w ->
            lp[0] = lp[0] * 0.93 + w * 0.07
            val env = sin(2.0 * PI * 0.12 * t) * 0.5 + 0.5 // 0..1 涨落
            (lp[0] * 0.55 + w * 0.2) * (0.25 + 0.75 * env)
        }
    }

    /** 风声：近似带通噪声 + 缓慢阵风调制，呼啸感。 */
    private fun wind(): ShortArray {
        val s = DoubleArray(2) { 0.0 }
        return buffer(4.0) { _, t, w ->
            s[0] = s[0] * 0.82 + w * 0.18
            s[1] = s[1] * 0.5 + (w - s[0]) * 0.5 // 近似带通 → 呼啸
            val gust = 0.35 + 0.65 * (sin(2.0 * PI * 0.07 * t) * 0.5 + 0.5)
            s[1] * 0.95 * gust
        }
    }

    /** 篝火：棕噪声底噪 + 偶发爆裂噼啪声。 */
    private fun fire(): ShortArray {
        val brown = DoubleArray(1) { 0.0 }
        return buffer(3.0) { _, _, w ->
            brown[0] = (brown[0] + 0.014 * w).coerceIn(-1.0, 1.0) * 0.997
            val crackle = if (Random.nextDouble() < 0.007) Random.nextDouble() * 0.55 else 0.0
            brown[0] * 0.72 + crackle
        }
    }

    /** 纯白噪音。 */
    private fun white(): ShortArray = buffer(2.0) { _, _, w -> w * 0.6 }

    /** 粉红噪音：多级一阶低通叠加，近似 -3dB/倍频。 */
    private fun pink(): ShortArray {
        val b = DoubleArray(3) { 0.0 }
        return buffer(3.0) { _, _, w ->
            b[0] = 0.98 * b[0] + 0.02 * w
            b[1] = 0.95 * b[1] + 0.05 * w
            b[2] = 0.90 * b[2] + 0.10 * w
            (b[0] * 0.5 + b[1] * 0.3 + b[2] * 0.2) * 0.85
        }
    }

    /** 风扇/空调：气流底噪 + 极淡电机低频。 */
    private fun fan(): ShortArray {
        val hum = DoubleArray(1) { 0.0 }
        return buffer(3.0) { _, t, w ->
            hum[0] = hum[0] * 0.96 + w * 0.04
            val motor = sin(2.0 * PI * 120.0 * t) * 0.04
            (hum[0] * 0.6 + w * 0.22 + motor) * 0.8
        }
    }
}
