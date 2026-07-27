package com.tickclear.app.domain.assistant

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * best-effort Opus 编解码封装（基于 Android MediaCodec）。
 *
 * - Android 多数设备仅提供 Opus 解码器，编码器不保证存在；
 * - encodeFrame / decodeFrame 任一环节失败均返回 null（优雅降级，绝不抛异常中断调用方）；
 * - 不引入原生 libopus，规避 NDK / 镜像缺包风险。
 *
 * 标准小智音频参数：16kHz / 单声道 / 16bit / 每帧 60ms
 * → 每帧 960 样本 = 1920 字节 PCM。
 */
class OpusCodec {

    private val sampleRate = 16000
    private val channels = 1
    private val frameSamples = sampleRate * 60 / 1000 // 960
    private val frameBytes = frameSamples * 2         // 1920 (16-bit)

    private val mime = "audio/opus"
    private val timeoutUs = 5_000L

    private val encLock = Any()
    private val decLock = Any()
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private var encoderProbed: Boolean? = null
    private var decoderProbed: Boolean? = null

    /** 解码目标采样率（由服务端 audio_params / tts-start 动态设定，默认 16000）。 */
    @Volatile var preferredDecodeRate: Int = 16000
    private var decoderRate: Int = -1

    private fun encFormat(): MediaFormat =
        MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameBytes)
        }

    private fun decFormat(): MediaFormat =
        MediaFormat.createAudioFormat(mime, preferredDecodeRate, channels).apply {
            setByteBuffer("csd-0", buildOpusHead(preferredDecodeRate))
        }

    /** 设备是否提供 Opus 编码器（决定是否启用语音输入）。结果缓存避免逐帧重探。 */
    fun isEncoderAvailable(): Boolean {
        encoderProbed?.let { return it }
        val ok = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .findEncoderForFormat(encFormat()) != null
        }.getOrDefault(false)
        encoderProbed = ok
        return ok
    }

    /** 设备是否提供 Opus 解码器（决定是否播放服务端 TTS 音频）。结果缓存避免逐帧重探。 */
    fun isDecoderAvailable(): Boolean {
        decoderProbed?.let { return it }
        val ok = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .findDecoderForFormat(decFormat()) != null
        }.getOrDefault(false)
        decoderProbed = ok
        return ok
    }

    /** 编码一帧 PCM(1920B) 为 Opus 包；长度不足或失败返回 null。 */
    fun encodeFrame(pcm16: ByteArray): ByteArray? = synchronized(encLock) {
        if (pcm16.size < frameBytes) return null
        runCatching {
            val codec = ensureEncoder() ?: return null
            val inIdx = codec.dequeueInputBuffer(timeoutUs)
            if (inIdx < 0) return null
            codec.getInputBuffer(inIdx)?.apply {
                clear()
                put(pcm16, 0, frameBytes)
            }
            codec.queueInputBuffer(inIdx, 0, frameBytes, 0, 0)
            val info = MediaCodec.BufferInfo()
            val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outIdx >= 0) {
                val out = codec.getOutputBuffer(outIdx)
                val data = ByteArray(info.size)
                out?.get(data)
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return null
                return data
            }
            null
        }.getOrDefault(null)
    }

    /** 解码一帧 Opus 包为 PCM(16bit)；失败返回 null。 */
    fun decodeFrame(opus: ByteArray): ByteArray? = synchronized(decLock) {
        runCatching {
            val codec = ensureDecoder() ?: return null
            val inIdx = codec.dequeueInputBuffer(timeoutUs)
            if (inIdx < 0) return null
            codec.getInputBuffer(inIdx)?.apply {
                clear()
                put(opus, 0, opus.size)
            }
            codec.queueInputBuffer(inIdx, 0, opus.size, 0, 0)
            val info = MediaCodec.BufferInfo()
            val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outIdx >= 0) {
                val out = codec.getOutputBuffer(outIdx)
                val data = ByteArray(info.size)
                out?.get(data)
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return null
                return data
            }
            null
        }.getOrDefault(null)
    }

    private fun ensureEncoder(): MediaCodec? {
        encoder?.let { return it }
        if (!isEncoderAvailable()) return null
        val name = MediaCodecList(MediaCodecList.ALL_CODECS).findEncoderForFormat(encFormat()) ?: return null
        return MediaCodec.createByCodecName(name).apply {
            configure(encFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }.also { encoder = it }
    }

    private fun ensureDecoder(): MediaCodec? {
        decoder?.let { if (decoderRate == preferredDecodeRate) return it }
        // 采样率变化：旧解码器输出 PCM 速率不匹配，必须释放重建（codec 配置不可热改）。
        if (decoder != null) {
            runCatching { decoder?.release() }
            decoder = null
            decoderRate = -1
        }
        if (!isDecoderAvailable()) return null
        val name = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(decFormat()) ?: return null
        return MediaCodec.createByCodecName(name).apply {
            configure(decFormat(), null, null, 0)
            start()
        }.also { decoder = it; decoderRate = preferredDecodeRate }
    }

    /** 构造最小 OpusHead（csd-0）：magic(8) ver(1) ch(1) preskip(2) rate(4) gain(2) map(1)。 */
    private fun buildOpusHead(rate: Int): ByteBuffer =
        ByteBuffer.allocate(19).apply {
            order(java.nio.ByteOrder.LITTLE_ENDIAN)
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1)                 // version
            put(channels.toByte()) // channels
            putShort(0)            // pre-skip
            putInt(rate)           // sample rate
            putShort(0)            // gain
            put(0)                 // channel mapping family
            rewind()
        }

    fun release() {
        synchronized(encLock) {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            encoder = null
            encoderProbed = null
        }
        synchronized(decLock) {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            decoder = null
            decoderProbed = null
            decoderRate = -1
        }
    }
}
