package com.tickclear.app.domain.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.min

/**
 * 麦克风采集：16kHz / 单声道 / 16bit，按 Opus 帧长(1920B)切帧后回调。
 * best-effort：初始化失败（无权限 / 设备不支持）返回 false，由调用方降级文本输入。
 * 调用前需确保已授予 RECORD_AUDIO 权限（本类不自行申请）。
 */
class AudioCapture {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var running = false

    @SuppressLint("MissingPermission")
    fun start(sampleRate: Int = 16000, onFrame: (ByteArray) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (e: Exception) {
            Log.w("AudioCapture", "init failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        record = rec
        running = true
        rec.startRecording()

        val frameSize = sampleRate * 60 / 1000 * 2 // 1920 字节
        val readBuf = ByteArray(minBuf)
        thread = Thread({
            val frame = ByteArray(frameSize)
            var filled = 0
            while (running) {
                val n = try {
                    rec.read(readBuf, 0, readBuf.size)
                } catch (e: Exception) {
                    Log.w("AudioCapture", "read failed", e)
                    break
                }
                if (n <= 0) break
                var off = 0
                while (off < n) {
                    val need = min(frameSize - filled, n - off)
                    System.arraycopy(readBuf, off, frame, filled, need)
                    filled += need
                    off += need
                    if (filled == frameSize) {
                        runCatching { onFrame(frame.copyOf()) }
                        filled = 0
                    }
                }
            }
        }, "AudioCapture").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        thread?.join(500)
        thread = null
        runCatching { record?.release() }
        record = null
    }

    /**
     * 累积采集模式：将整段录音缓冲进内存，停止时一次性回调完整 PCM（16bit）。
     * 用于云 ASR（需整段音频文件而非实时帧流）。其余语义同 [start]。
     * 权限：与 [start] 一致，调用前须已授予 RECORD_AUDIO（由 UI 层守卫）。
     */
    @SuppressLint("MissingPermission")
    fun startAccumulate(sampleRate: Int = 16000, onComplete: (ByteArray) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (e: Exception) {
            Log.w("AudioCapture", "init failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        record = rec
        running = true
        rec.startRecording()

        val readBuf = ByteArray(minBuf)
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        thread = Thread({
            while (running) {
                val n = try {
                    rec.read(readBuf, 0, readBuf.size)
                } catch (e: Exception) {
                    Log.w("AudioCapture", "read failed", e)
                    break
                }
                if (n <= 0) break
                val copy = readBuf.copyOf(n)
                chunks.add(copy)
                total += n
            }
            // 拼接全部 PCM 片段
            val pcm = ByteArray(total)
            var off = 0
            for (c in chunks) {
                System.arraycopy(c, 0, pcm, off, c.size)
                off += c.size
            }
            runCatching { onComplete(pcm) }
        }, "AudioCapture-accum").also { it.start() }
        return true
    }
}
