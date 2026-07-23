package com.tickclear.app.domain.assistant

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 云厂商签名所需的纯 JDK 密码学原语（零新依赖）。
 *
 * 仅封装 HMAC-SHA1 / HMAC-SHA256 / SHA-256 以及 RFC 3986 百分比编码，
 * 供腾讯云（TC3-HMAC-SHA256）与阿里云（RPC HMAC-SHA1）ASR 管线复用。
 */
object Signing {
    private const val HEX = "0123456789abcdef"

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(HEX[v ushr 4])
            append(HEX[v and 0x0F])
        }
    }

    fun sha256Hex(input: ByteArray): String = hex(sha256(input))

    fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA1").run {
            init(SecretKeySpec(key, "HmacSHA1"))
            doFinal(data)
        }

    /**
     * RFC 3986 百分比编码（阿里云 RPC 签名要求）：
     * 仅保留 A-Za-z0-9-_.~，其余字节按 %XX 编码，空格编码为 %20。
     */
    fun percentEncode(value: String): String {
        val sb = StringBuilder()
        for (c in value.toByteArray(StandardCharsets.UTF_8)) {
            val b = c.toInt() and 0xFF
            if ((b >= 'A'.code && b <= 'Z'.code) ||
                (b >= 'a'.code && b <= 'z'.code) ||
                (b >= '0'.code && b <= '9'.code) ||
                b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code
            ) {
                sb.append(b.toChar())
            } else {
                sb.append('%')
                sb.append(HEX[(b ushr 4) and 0x0F])
                sb.append(HEX[b and 0x0F])
            }
        }
        return sb.toString()
    }
}
