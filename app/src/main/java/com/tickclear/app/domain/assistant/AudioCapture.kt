package com.tickclear.app.domain.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.tickclear.app.domain.log.AppLogger
import java.io.File
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
            AppLogger.w("AudioCapture", "init failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        record = rec
        running = true
        val frameSize = sampleRate * 60 / 1000 * 2 // 1920 字节
        // startRecording 可能抛异常（部分设备状态异常）；失败须释放并复位，否则 record/running 残留导致 AudioRecord 泄漏。
        try {
            rec.startRecording()
            AppLogger.d("AudioCapture", "start OK sampleRate=$sampleRate minBuf=$minBuf frameSize=$frameSize")
        } catch (e: Exception) {
            AppLogger.w("AudioCapture", "startRecording failed", e)
            running = false
            rec.release()
            record = null
            return false
        }

        val readBuf = ByteArray(minBuf)
        thread = Thread({
            val frame = ByteArray(frameSize)
            var filled = 0
            // V2.8X+：诊断计数器，便于"语音不能用"时定位"录音没起/read 阻塞/回调未触发"。
            var readCount = 0
            var readErrCount = 0
            var readZeroCount = 0
            var frameCount = 0
            while (running) {
                val n = try {
                    rec.read(readBuf, 0, readBuf.size)
                } catch (e: Exception) {
                    // V2.8X+：不再静默 break；v 级日志后继续循环，让后续帧有机会。
                    // 旧实现一遇异常就退出线程，导致"语音不能用"无任何线索。
                    readErrCount++
                    if (readErrCount <= 3 || readErrCount % 50 == 0) {
                        AppLogger.w("AudioCapture", "read 异常 第${readErrCount}次（继续）", e)
                    }
                    continue
                }
                readCount++
                if (n <= 0) {
                    // 首次返回 0 是某些设备的预热行为，v 级记录但不退出。
                    readZeroCount++
                    if (readZeroCount <= 5 || readZeroCount % 50 == 0) {
                        AppLogger.v("AudioCapture", "read 返回 $n（可能是设备预热，第${readZeroCount}次）")
                    }
                    continue
                }
                var off = 0
                while (off < n) {
                    val need = min(frameSize - filled, n - off)
                    System.arraycopy(readBuf, off, frame, filled, need)
                    filled += need
                    off += need
                    if (filled == frameSize) {
                        frameCount++
                        val payload = frame.copyOf()
                        // v 级日志：每帧触发即打，避免被高频日志淹没但又能完整跟踪。
                        AppLogger.v("AudioCapture", "→ onFrame ${payload.size}B 第${frameCount}帧")
                        runCatching { onFrame(payload) }
                        filled = 0
                    }
                }
            }
            AppLogger.d("AudioCapture", "线程退出 readCount=$readCount readErr=$readErrCount readZero=$readZeroCount frameCount=$frameCount")
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
     * 累积采集模式（V2.58）：边录边把裸 PCM 写入 [pcmFile]，停止时回调该文件，
     * 由调用方流式封装为 WAV。相比旧实现「整段缓冲进内存再拼接」，峰值内存仅一个音频缓冲区，
     * 彻底消除超长录音（>5min）的 OOM 风险。回调参数为原始 PCM 文件，调用方负责使用/清理。
     * 权限：与 [start] 一致，调用前须已授予 RECORD_AUDIO（由 UI 层守卫）。
     */
    @SuppressLint("MissingPermission")
    fun startAccumulate(
        sampleRate: Int = 16000,
        pcmFile: File,
        onComplete: (File) -> Unit,
    ): Boolean {
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
            AppLogger.w("AudioCapture", "init failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        record = rec
        running = true
        try {
            rec.startRecording()
        } catch (e: Exception) {
            AppLogger.w("AudioCapture", "startRecording failed", e)
            running = false
            rec.release()
            record = null
            return false
        }

        val readBuf = ByteArray(minBuf)
        thread = Thread({
            try {
                // 边录边写临时文件，避免整段 PCM 驻留内存
                pcmFile.outputStream().use { out ->
                    while (running) {
                        val n = try {
                            rec.read(readBuf, 0, readBuf.size)
                        } catch (e: Exception) {
                            AppLogger.w("AudioCapture", "read failed", e)
                            break
                        }
                        if (n <= 0) break
                        out.write(readBuf, 0, n)
                    }
                }
            } finally {
                // 采集结束必须释放 AudioRecord，否则调用方若未再调 stop() 将泄露至进程死亡。
                // 与 stop() 的清理一致（release 后置空 + 关 running，使后续 stop() 成为空操作）。
                running = false
                runCatching { record?.release() }
                record = null
                runCatching { onComplete(pcmFile) }
            }
        }, "AudioCapture-accum").also { it.start() }
        return true
    }
}
