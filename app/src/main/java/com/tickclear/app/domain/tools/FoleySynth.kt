package com.tickclear.app.domain.tools

import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 模拟解压音效合成器（V2.9++ 工具箱「模拟解压」分类）。
 * 复用 AnimalSynth 的 AudioTrack 实时合成思路，零额外依赖、本地播放。
 * 提供的音效：易拉罐喷溅 / 木鱼敲击 / 打火机点火 / 烟花爆炸 / 吹灭 / 弹珠碰撞。
 * 这些是「拟物」音效，主打解压好玩，并非真实录音。
 */
object FoleySynth {
    private const val SR = 44100
    private var current: AudioTrack? = null

    /** 停止并释放当前正在播放的音轨。 */
    fun stop() {
        runCatching {
            current?.stop()
            current?.release()
        }
        current = null
    }

    fun play(key: String) {
        val samples = when (key) {
            "can" -> canSpray()
            "wood" -> woodKnock()
            "lighter" -> lighterFlick()
            "firework" -> firework()
            "blow" -> blowOut()
            "pop" -> pop()
            else -> return
        }
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            current = track
            track.play()
        }
    }

    // ---------- 合成基元 ----------
    /** 确定性正弦音：freq/gain 为时间(秒)函数，可选叠加白噪声。 */
    private fun tone(
        dur: Double,
        freq: (Double) -> Double,
        gain: (Double) -> Double,
        noise: Double = 0.0,
    ): ShortArray {
        val n = (dur * SR).toInt()
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

    /** 白噪声：gain 为时间(秒)函数（通常做淡出包络）。 */
    private fun noise(dur: Double, gain: (Double) -> Double): ShortArray {
        val n = (dur * SR).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val s = (Random.nextDouble() * 2 - 1) * gain(t)
            out[i] = (s.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
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

    // ---------- 各音效 ----------
    /** 易拉罐开罐喷溅：高频嘶嘶白噪声快速淡出。 */
    private fun canSpray() = concat(
        noise(0.7) { t -> 0.55 * (1 - t / 0.7) },
        silence(0.02),
        tone(0.08, { 520.0 }, g(0.08, 0.02, 0.5)),
    )

    /** 木鱼敲击：短促低音“笃” + 一点木质咔哒。 */
    private fun woodKnock() = concat(
        tone(0.06, { 200.0 }, { t -> (1 - t / 0.06) * 0.9 }),
        noise(0.02, { 0.6 }),
    )

    /** 打火机点火：金属咔哒 + 火焰呼一声。 */
    private fun lighterFlick() = concat(
        noise(0.04, { 0.8 }),
        silence(0.02),
        noise(0.2, { t -> 0.5 * (1 - t / 0.2) }),
    )

    /** 烟花爆炸：低频“咚” + 宽频脆响。 */
    private fun firework() = concat(
        tone(0.12, { 80.0 }, { t -> (1 - t / 0.12) * 0.8 }),
        silence(0.02),
        noise(0.4, { t -> 0.6 * (1 - t / 0.4) }),
    )

    /** 吹灭蜡烛：一声短促的气流。 */
    private fun blowOut() = noise(0.25, { t -> 0.5 * (1 - t / 0.25) })

    /** 弹珠碰撞：清脆“叮”。 */
    private fun pop() = tone(0.05, { 320.0 }, { t -> (1 - t / 0.05) * 0.9 })
}
