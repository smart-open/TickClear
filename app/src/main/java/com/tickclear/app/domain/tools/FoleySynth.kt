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
 * 模拟解压音效合成器（V2.9++ 工具箱「模拟解压」分类）。
 * 复用 AnimalSynth 的 AudioTrack 实时合成思路，零额外依赖、本地播放。
 * 提供的音效：木鱼敲击 / 打火机点火 / 烟花爆炸 / 吹灭 / 弹珠碰撞。
 * 这些是「拟物」音效，主打解压好玩，并非真实录音。
 *
 * 木鱼敲击（V2.11++）：优先播放 res/raw/wood_knock 的真实录音（MediaPlayer），
 * 缺失或播放失败时回退到内置合成音，保证「点了一定有声音」。
 */
object FoleySynth {
    private const val TAG = "FoleySynth"
    private const val SR = 44100
    private var current: AudioTrack? = null
    private var mp: MediaPlayer? = null

    /** 停止并释放当前正在播放的合成音轨与录音。 */
    fun stop() {
        releaseTrack()
        releaseMp()
    }

    private fun releaseTrack() {
        runCatching {
            current?.stop()
            current?.release()
        }.onFailure { AppLogger.w(TAG, "AudioTrack 释放异常：${it.message}") }
        current = null
    }

    private fun releaseMp() {
        runCatching { mp?.release() }
        mp = null
    }

    /**
     * 木鱼敲击：优先播放真实录音 wood_knock（MediaPlayer，需在主线程调用），
     * 录音缺失/失败时回退合成音。录音来自公开木鱼音效（mokugyo），CC0 可用。
     */
    fun playWood(context: Context) {
        val player = runCatching { MediaPlayer.create(context, R.raw.wood_knock) }.getOrNull()
        if (player != null) {
            releaseMp()
            mp = player.apply {
                setOnCompletionListener { releaseMp() }
                setOnErrorListener { _, _, _ -> releaseMp(); true }
                start()
            }
        } else {
            AppLogger.w(TAG, "wood_knock 录音不可用，回退合成")
            synthWood()
        }
    }

    /**
     * 烟花爆炸：优先播放真实录音 firework_boom（MediaPlayer，主线程调用），
     * 缺失/失败时回退合成音。录音取自 Freesound #624413「Firework single shot」(MilanKovanda)，
     * CC0 公有领域；经 ffmpeg 转 16-bit/44.1k/单声道，并裁掉前 3.1s 静音段让爆炸即时触发。
     */
    fun playFirework(context: Context) {
        val player = runCatching { MediaPlayer.create(context, R.raw.firework_boom) }.getOrNull()
        if (player != null) {
            releaseMp()
            mp = player.apply {
                setOnCompletionListener { releaseMp() }
                setOnErrorListener { _, _, _ -> releaseMp(); true }
                start()
            }
        } else {
            AppLogger.w(TAG, "firework_boom 录音不可用，回退合成")
            play("firework")
        }
    }

    /**
     * 烟花发射「咻」：优先播放真实录音 launch_whistle（MediaPlayer，主线程调用），
     * 缺失/失败时回退合成音。录音为火箭升空嗖嗖声，取自 Freesound 用户 northern87（CC0 公有领域）；
     * 经 tosound.com 预览镜像获取，再由 ffmpeg 裁剪为 0.75s、转 16-bit/44.1k/单声道。
     */
    fun playLaunch(context: Context) {
        val player = runCatching { MediaPlayer.create(context, R.raw.launch_whistle) }.getOrNull()
        if (player != null) {
            releaseMp()
            mp = player.apply {
                setOnCompletionListener { releaseMp() }
                setOnErrorListener { _, _, _ -> releaseMp(); true }
                start()
            }
        } else {
            AppLogger.w(TAG, "launch_whistle 录音不可用，回退合成")
            play("launch")
        }
    }

    /**
     * 弹珠碰撞：优先播放真实录音 marble_click（MediaPlayer，主线程调用），
     * 缺失/失败时回退合成音。录音取自 Freesound #401741「marble spilling onto wooden table」(PMBROWNE)，
     * CC0 公有领域；经 ffmpeg 从 16s 原始录音裁出首击 ~0.4s 清脆段，转 16-bit/44.1k/单声道。
     */
    fun playPop(context: Context) {
        val player = runCatching { MediaPlayer.create(context, R.raw.marble_click) }.getOrNull()
        if (player != null) {
            releaseMp()
            mp = player.apply {
                setOnCompletionListener { releaseMp() }
                setOnErrorListener { _, _, _ -> releaseMp(); true }
                start()
            }
        } else {
            AppLogger.w(TAG, "marble_click 录音不可用，回退合成")
            play("pop")
        }
    }

    fun play(key: String) {
        val samples = when (key) {
            "wood" -> synthWood()
            "lighter" -> lighterFlick()
            "lid_close" -> lidClose()
            "firework" -> firework()
            "launch" -> launchWhistle()
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
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            current = track
            track.play()
        }.onFailure { AppLogger.w(TAG, "音效合成/播放失败（key=$key）：${it.message}") }
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
    /**
     * 木鱼敲击（合成兜底）：木质「笃」声——中频短促音 + 一记咔哒噪声。
     * 比旧版更亮（520Hz）更突出，确保无声卡/无录音环境也能清晰听到。
     */
    private fun synthWood() = concat(
        tone(0.12, { 520.0 }, { t -> (1 - t / 0.12) * (1 - t / 0.12) * 0.95 }),
        noise(0.015, { 0.5 }),
    )

    /** 打火机点火：金属咔哒 + 火焰呼一声。 */
    private fun lighterFlick() = concat(
        noise(0.04, { 0.8 }),
        silence(0.02),
        noise(0.2, { t -> 0.5 * (1 - t / 0.2) }),
    )

    /** 烟花爆炸：低频「咚」 + 宽频脆响。 */
    private fun firework() = concat(
        tone(0.12, { 80.0 }, { t -> (1 - t / 0.12) * 0.8 }),
        silence(0.02),
        noise(0.4, { t -> 0.6 * (1 - t / 0.4) }),
    )

    /** 吹灭蜡烛：一声短促的气流。 */
    private fun blowOut() = noise(0.25, { t -> 0.5 * (1 - t / 0.25) })

    /** 弹珠碰撞：清脆「叮」。 */
    private fun pop() = tone(0.05, { 320.0 }, { t -> (1 - t / 0.05) * 0.9 })

    /**
     * 烟花发射「咻」：高频口哨由低到高急升后轻收，约 0.3s。
     * 在火箭起飞时播放，与抵达时的爆炸 boom 形成「先咻后响」的真实节奏。
     */
    private fun launchWhistle() = tone(
        0.30,
        { t -> 380.0 + 1500.0 * (t / 0.30) },
        { t -> (1 - t / 0.30).coerceAtLeast(0.0) * 0.30 },
    )

    /** 打火机盖合拢：短促金属「咔嗒」——高频噪声爆发 + 一记金属泛音。 */
    private fun lidClose() = concat(
        noise(0.03, { 0.7 }),
        tone(0.045, { 1150.0 }, { t -> (1 - t / 0.045) * 0.22 }),
    )
}
