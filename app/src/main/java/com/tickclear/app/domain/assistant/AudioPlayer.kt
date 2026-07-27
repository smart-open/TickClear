package com.tickclear.app.domain.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tickclear.app.domain.log.AppLogger

/**
 * PCM 播放：16kHz / 单声道 / 16bit，流式 write。
 * 使用 AudioTrack.Builder（避免已弃用的 streamType 构造器）。
 * best-effort：初始化失败静默降级为「无声音」，文本仍照常展示。
 */
class AudioPlayer {

    private var track: AudioTrack? = null
    private var rate = -1

    fun init(sampleRate: Int = 16000): Boolean {
        // 采样率变化时必须重建 AudioTrack，否则以错误速率播放（变速/无声）。
        if (track != null && rate == sampleRate) return true
        release()
        rate = sampleRate
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            AppLogger.w("AudioPlayer", "init failed", e)
            return false
        }

        this.track = track
        runCatching { track.play() }
        return true
    }

    fun play(pcm16: ByteArray) {
        runCatching { track?.write(pcm16, 0, pcm16.size) }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }
}
