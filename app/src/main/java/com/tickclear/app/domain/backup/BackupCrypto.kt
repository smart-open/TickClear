package com.tickclear.app.domain.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 备份加密（V2.6）：基于 AndroidKeystore 的 AES/GCM/NoPadding（256 位）。
 *
 * - 密钥由 AndroidKeystore 托管，不落盘、不可导出，设备解锁后可用；
 *   **卸载重装会丢失密钥** → 旧加密备份无法再解密（属预期安全边界，已在设置页说明）。
 * - 零新依赖：仅用框架 `javax.crypto` + `android.security.keystore`（API 23+，
 *   本应用 minSdk 26，满足）。
 * - 信封格式：`TCB1`(4 字节魔数) + IV(12 字节) + 密文(GCM 含 16 字节 tag)。
 *   导入时先读魔数，命中即解密，否则按明文 JSON 兼容旧备份。
 */
object BackupCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "tickclear_backup_key"
    private const val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128
    private const val MAGIC = "TCB1"

    /** 备份文件是否经本工具加密（用于导入时自动识别）。 */
    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= 4 && data.copyOfRange(0, 4).toString(Charsets.US_ASCII) == MAGIC

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** 明文 JSON → 加密信封字节。 */
    fun encrypt(plainText: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LEN, iv))
        }
        val cipherText = cipher.doFinal(plainText)
        return ByteArrayOutputStream(4 + GCM_IV_LEN + cipherText.size).apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            write(iv)
            write(cipherText)
        }.toByteArray()
    }

    /**
     * 加密信封字节 → 明文 JSON（自动识别魔数）。
     *
     * 文件被截断（拷贝中断 / U 盘拔出 / 云盘同步一半）时，只有魔数没有完整 IV，
     * 直接 copyOfRange 会抛 ArrayIndexOutOfBoundsException 这种技术异常。
     * 这里先做长度校验并转成带错误码的 [AppException]，保证 UI 拿到的是「备份文件损坏」而不是崩溃栈。
     */
    fun decrypt(data: ByteArray): ByteArray {
        if (!isEncrypted(data)) return data
        // 魔数 + IV + 至少 1 字节密文 + GCM tag(16B)
        val minSize = 4 + GCM_IV_LEN + 1 + GCM_TAG_LEN / 8
        if (data.size < minSize) {
            throw AppException(
                ErrorCode.IMPORT_PARSE_FAILED,
                detail = "truncated envelope: ${data.size}B < ${minSize}B",
            )
        }
        val iv = data.copyOfRange(4, 4 + GCM_IV_LEN)
        val cipherText = data.copyOfRange(4 + GCM_IV_LEN, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LEN, iv))
        }
        return cipher.doFinal(cipherText)
    }
}
