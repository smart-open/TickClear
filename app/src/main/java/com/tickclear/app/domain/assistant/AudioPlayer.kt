package com.tickclear.app.domain.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * PCM 播放：16kHz / 单声道 / 16bit，流式 write。
 * 使用 AudioTrack.Builder（避免已弃用的 streamType 构造器）。
 * best-effort：初始化失败静默降级为「无声音」，文本仍照常展示。
 */
class AudioPlayer {

    private var track: AudioTrack? = null

    fun init(sampleRate: Int = 16000): Boolean {
        if (track != null) return true
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
            Log.w("AudioPlayer", "init failed", e)
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
