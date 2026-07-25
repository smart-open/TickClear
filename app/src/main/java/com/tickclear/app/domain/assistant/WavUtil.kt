package com.tickclear.app.domain.assistant

import java.io.File
import java.io.RandomAccessFile

/**
 * 将 16bit PCM 裸流封装为标准 WAV 文件（RIFF / WAVE / PCM）。
 * 默认 16kHz、单声道、16bit，与 [AudioCapture] 采集参数一致。
 * 纯 JDK 实现，无任何外部依赖。
 */
object WavUtil {
    private const val SAMPLE_RATE = 16000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    fun writePcm(pcm: ByteArray, out: File, sampleRate: Int = SAMPLE_RATE): File {
        val dataSize = pcm.size
        val totalSize = 36 + dataSize
        RandomAccessFile(out, "rw").use { raf ->
            // RIFF header
            raf.write("RIFF".toAscii())
            raf.writeIntLe(totalSize)
            raf.write("WAVE".toAscii())
            // fmt chunk
            raf.write("fmt ".toAscii())
            raf.writeIntLe(16) // PCM chunk size
            raf.writeShortLe(1) // audio format = PCM
            raf.writeShortLe(CHANNELS)
            raf.writeIntLe(sampleRate)
            val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
            raf.writeIntLe(byteRate)
            val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
            raf.writeShortLe(blockAlign)
            raf.writeShortLe(BITS_PER_SAMPLE)
            // data chunk
            raf.write("data".toAscii())
            raf.writeIntLe(dataSize)
            raf.write(pcm)
        }
        return out
    }

    /**
     * V2.58：从裸 PCM 文件流式封装为 WAV，避免把整段录音读入内存（长录音 OOM 风险）。
     * 头部写出后按 8KB 缓冲从 [rawPcm] 拷贝数据，峰值内存仅一个缓冲区。
     */
    fun writePcmFromFile(rawPcm: File, out: File, sampleRate: Int = SAMPLE_RATE): File {
        val dataSize = rawPcm.length().toInt()
        val totalSize = 36 + dataSize
        RandomAccessFile(out, "rw").use { raf ->
            raf.write("RIFF".toAscii())
            raf.writeIntLe(totalSize)
            raf.write("WAVE".toAscii())
            raf.write("fmt ".toAscii())
            raf.writeIntLe(16)
            raf.writeShortLe(1)
            raf.writeShortLe(CHANNELS)
            raf.writeIntLe(sampleRate)
            val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
            raf.writeIntLe(byteRate)
            val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
            raf.writeShortLe(blockAlign)
            raf.writeShortLe(BITS_PER_SAMPLE)
            raf.write("data".toAscii())
            raf.writeIntLe(dataSize)
            rawPcm.inputStream().use { inStream ->
                val buf = ByteArray(8192)
                var read: Int
                while (inStream.read(buf).also { read = it } != -1) {
                    raf.write(buf, 0, read)
                }
            }
        }
        return out
    }

    private fun String.toAscii(): ByteArray = toByteArray(Charsets.US_ASCII)

    private fun RandomAccessFile.writeIntLe(v: Int) {
        writeByte(v and 0xFF)
        writeByte((v ushr 8) and 0xFF)
        writeByte((v ushr 16) and 0xFF)
        writeByte((v ushr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeShortLe(v: Int) {
        writeByte(v and 0xFF)
        writeByte((v ushr 8) and 0xFF)
    }
}
