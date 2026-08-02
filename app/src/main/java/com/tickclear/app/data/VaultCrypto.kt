package com.tickclear.app.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码保险箱加密原语（V2.9，零新依赖）：
 * - 主密钥由用户口令经 PBKDF2WithHmacSHA256 派生（AES-256-GCM）。
 * - 条目字段逐个经 AES/GCM 加密，密文随加密后的 blob 持久化；
 *   即便设备被物理取出，无口令也无法解密（区别于仅依赖 Keystore 的 EncryptedSharedPreferences）。
 * - 安全问题答案同样走 PBKDF2 慢哈希，用于「忘记口令」校验。
 */
object VaultCrypto {
    private const val ALGO = "AES"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128
    private const val PBKDF2_ITER = 120_000
    private const val PBKDF2_KEY_LEN = 256
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"

    fun randomSalt(len: Int = 16): ByteArray {
        val b = ByteArray(len)
        SecureRandom().nextBytes(b)
        return b
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITER, PBKDF2_KEY_LEN)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ALGO)
    }

    /** AES-256-GCM 加密；输出 = Base64( iv(12) || ciphertext )。 */
    fun encrypt(key: SecretKey, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return Base64.getEncoder().encodeToString(out)
    }

    /** 对应 [encrypt] 的解密；口令错误时 GCM 校验失败抛异常，由调用方捕获判定。 */
    fun decrypt(key: SecretKey, b64: String): String {
        val data = Base64.getDecoder().decode(b64)
        val iv = data.copyOfRange(0, GCM_IV_LEN)
        val ct = data.copyOfRange(GCM_IV_LEN, data.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    /** 安全问题答案的慢哈希（与派生密钥同参数），仅用于「忘记口令」校验，不存储明文答案。 */
    fun hashAnswer(answer: CharArray, salt: ByteArray): String {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(answer, salt, PBKDF2_ITER, PBKDF2_KEY_LEN)
        return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
    }

    fun bytesToB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun b64ToBytes(b64: String): ByteArray = Base64.getDecoder().decode(b64)
}
