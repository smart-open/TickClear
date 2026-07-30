package com.tickclear.app.domain.assistant

import com.theeasiestway.opus.Constants
import com.theeasiestway.opus.Opus
import com.tickclear.app.domain.log.AppLogger

/**
 * Opus 编解码封装（V2.8X++ 长期方案）。
 *
 * 采用 theeasiestway/android-opus-codec（com.theeasiestway.opus，封装官方 libopus 1.3.1），
 * 其预编译 .so 覆盖 armeabi-v7a / arm64-v8a / x86 / x86_64 全部 ABI（含 64 位），
 * 彻底解决原 martoreto/opuscodec 仅含 32 位 libsenz.so、arm64 设备 dlopen 失败导致语音不可用的根因；
 * 也不再需要「剔除 arm64-v8a 目录 / 32 位回退」这类妥协方案，arm64 设备原生 64 位运行。
 *
 * - encodeFrame / decodeFrame 任一环节失败均返回 null（优雅降级，绝不抛异常中断调用方）。
 *
 * 标准小智音频参数：16kHz / 单声道 / 16bit / 每帧 60ms → 每帧 960 样本 = 1920 字节 PCM。
 */
class OpusCodec {

    private val encodeRate = 16000
    private val channels = 1
    private val encFrameSamples = encodeRate * 60 / 1000 // 960
    private val encFrameBytes = encFrameSamples * 2      // 1920 (16-bit)

    /** 解码目标采样率（由服务端 tts-start / audio_params 动态设定，默认 16000）。 */
    @Volatile var preferredDecodeRate: Int = 16000

    // 单一 Opus 句柄：编码器与解码器均在同实例内 init（库设计如此）。
    private var codec: Opus? = null
    private var encoderReady = false
    private var decoderReady = false
    private var decoderRate: Int = -1

    /**
     * 编码器可用性：首次调用时真实尝试初始化 native OpusEncoder（会触发 libopus.so 加载），
     * 结果缓存复用。arm64 设备缺失对应 .so 时如实返回 false，使上层回退系统 ASR / 明确禁用麦克风，
     * 而非进入编码循环静默失败。
     */
    @Volatile private var encoderAvailable: Boolean? = null
    fun isEncoderAvailable(): Boolean {
        encoderAvailable?.let { return it }
        val ok = runCatching { ensureEncoder() != null }.getOrDefault(false)
        encoderAvailable = ok
        if (!ok) AppLogger.e("OpusCodec", "isEncoderAvailable=false：libopus 原生库未成功加载（详见 native 初始化日志），语音 Opus 流不可用")
        return ok
    }

    /** 解码器与编码器同源 .so，可用性一致。 */
    fun isDecoderAvailable(): Boolean = isEncoderAvailable()

    /** 编码一帧 PCM(1920B) 为 Opus 包；长度不足或失败返回 null。 */
    fun encodeFrame(pcm16: ByteArray): ByteArray? {
        if (pcm16.size < encFrameBytes) {
            AppLogger.w("OpusCodec", "encodeFrame 失败：pcm 长度 ${pcm16.size} < ${encFrameBytes}B")
            return null
        }
        return runCatching {
            val c = ensureEncoder() ?: run {
                AppLogger.w("OpusCodec", "encodeFrame 失败：ensureEncoder 返回 null（native libopus 初始化失败）")
                return null
            }
            // 库直接以 16-bit PCM 字节流编为 Opus 字节流（小端 16bit 由库内部处理），返回 Opus 包字节数组。
            val out = c.encode(pcm16, Constants.FrameSize._960())
            AppLogger.v("OpusCodec", "→ encodeFrame OK 输出 ${out?.size ?: 0}B")
            out
        }.getOrElse { e ->
            AppLogger.e("OpusCodec", "encodeFrame 异常", e)
            null
        }
    }

    /** 解码一帧 Opus 包为 PCM(16bit)；失败返回 null。 */
    fun decodeFrame(opus: ByteArray): ByteArray? {
        return runCatching {
            val rate = if (preferredDecodeRate > 0) preferredDecodeRate else 16000
            val c = ensureDecoder(rate) ?: return null
            val frameSamples = rate * 60 / 1000
            val out = c.decode(opus, Constants.FrameSize.fromValue(frameSamples))
            AppLogger.v("OpusCodec", "← decodeFrame OK 输出 ${out?.size ?: 0}B")
            out
        }.getOrElse { e ->
            AppLogger.e("OpusCodec", "decodeFrame 异常", e)
            null
        }
    }

    @Synchronized
    private fun instance(): Opus {
        if (codec == null) codec = Opus()
        return codec!!
    }

    @Synchronized
    private fun ensureEncoder(): Opus? {
        if (encoderReady) return codec
        return runCatching {
            val c = instance()
            val rc = c.encoderInit(
                Constants.SampleRate._16000(),
                Constants.Channels.mono(),
                Constants.Application.voip(),
            )
            if (rc != 0) throw RuntimeException("encoderInit rc=$rc")
            c.encoderSetBitrate(Constants.Bitrate.instance(24_000))
            encoderReady = true
            c
        }.getOrElse { e ->
            AppLogger.e("OpusCodec", "native Opus 编码器初始化失败", e)
            null
        }
    }

    @Synchronized
    private fun ensureDecoder(rate: Int): Opus? {
        val c = runCatching { instance() }.getOrNull() ?: return null
        if (decoderReady && decoderRate == rate) return c
        return runCatching {
            if (decoderRate != -1) runCatching { c.decoderRelease() }
            val rc = c.decoderInit(sampleRateFor(rate), Constants.Channels.mono())
            if (rc != 0) throw RuntimeException("decoderInit rc=$rc")
            decoderReady = true
            decoderRate = rate
            c
        }.getOrElse { e ->
            AppLogger.e("OpusCodec", "native Opus 解码器初始化失败", e)
            null
        }
    }

    /** 将采样率映射到库支持的档位（8000/12000/16000/24000/48000），未列出则回退 16k。 */
    private fun sampleRateFor(rate: Int): Constants.SampleRate = when (rate) {
        8000 -> Constants.SampleRate._8000()
        12000 -> Constants.SampleRate._12000()
        24000 -> Constants.SampleRate._24000()
        48000 -> Constants.SampleRate._48000()
        else -> Constants.SampleRate._16000()
    }

    fun release() {
        runCatching { codec?.encoderRelease() }
        runCatching { codec?.decoderRelease() }
        codec = null
        encoderReady = false
        decoderReady = false
        decoderRate = -1
    }
}
