package com.tickclear.app.domain.tools

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 动物拟声（工具箱「动物拟声」，V2.9++）。
 *
 * 播放策略（V2.11++ 优化）：
 *  - 优先播放 `res/raw/animal_<key>` 里的真实录音（MediaPlayer，零额外依赖）；
 *  - 若该动物没有对应录音文件，则回退到内置合成音（AudioTrack 实时合成）。
 *
 * 真实录音来源：dog/cat/cow/sheep/chicken/lion/bird/frog 取自公开动物声音数据集；
 * duck/pig/tiger/horse 暂未找到可可靠下载的免费录音，继续走合成兜底，
 * 后续可把 CC0 录音按 `animal_<key>.wav` 放进 res/raw 即自动启用。
 */
object AnimalSynth {
    private const val TAG = "AnimalSynth"
    private const val SR = 44100
    private var current: AudioTrack? = null
    private var mp: MediaPlayer? = null

    /** 已内置真实录音的动物：key -> res/raw/animal_<key> 资源 id（静态引用，避免 lint 误判未使用）。 */
    private val RAW_SOUNDS = mapOf(
        "dog" to R.raw.animal_dog,
        "cat" to R.raw.animal_cat,
        "cow" to R.raw.animal_cow,
        "sheep" to R.raw.animal_sheep,
        "chicken" to R.raw.animal_chicken,
        "lion" to R.raw.animal_lion,
        "bird" to R.raw.animal_bird,
        "frog" to R.raw.animal_frog,
    )

    /** 该动物是否有内置真实录音。 */
    fun hasRecording(key: String): Boolean = RAW_SOUNDS.containsKey(key)

    /** 停止并释放当前正在播放的合成音轨与录音。 */
    fun stop() = releaseAll()

    private fun releaseAll() {
        // 未播放状态下 stop() 会抛 IllegalStateException，属预期；但不得裸吞（AGENT.md §3 禁裸 catch），
        // 记 w 级日志以便排查「音轨未释放」这类偶发问题。
        runCatching {
            current?.stop()
            current?.release()
        }.onFailure { AppLogger.w(TAG, "AudioTrack 释放异常：${it.message}") }
        current = null
        runCatching { mp?.release() }
        mp = null
    }

    /** 播放内置真实录音（key 须有对应录音，否则静默忽略；在主线程调用，Looper 存在）。 */
    fun playRaw(context: Context, key: String) {
        val resId = RAW_SOUNDS[key] ?: return
        releaseAll()
        runCatching {
            mp = MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { releaseMp() }
                setOnErrorListener { _, _, _ -> releaseMp(); true }
                start()
            }
        }.onFailure { AppLogger.w(TAG, "raw 录音播放失败（key=$key）：${it.message}") }
    }

    private fun releaseMp() {
        runCatching { mp?.release() }
        mp = null
    }

    /** 内置合成（无录音文件时的兜底，并非真实录音）。 */
    fun playSynth(key: String) {
        releaseAll()
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
