package com.tickclear.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码保险箱「生物识别快速解锁」加密层（V2.9+，依赖 androidx.biometric，用户显式批准破例引入）。
 *
 * 设计要点：
 * - 保险箱主密钥（AES-256-GCM 派生密钥的 raw bytes）用 Android Keystore 中一条
 *   「每次使用都需生物识别认证」的 AES 密钥加密封装落盘。生物识别通过时 Keystore 才允许解密出主密钥，
 *   进而解锁保险箱。口令明文从不落盘，口令 / 安全问题始终可恢复。
 * - 封装文件：filesDir/vault_bio.bin（IV(12) || ciphertext）。
 * - 绑定（wrap）与解锁（unwrap）均在 [androidx.biometric.BiometricPrompt] 认证成功的回调中执行，
 *   此时入参 cipher 已通过生物识别认证，可直接用于加解密。
 */
object VaultBioCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "tickclear_vault_bio"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128
    private const val FILE_NAME = "vault_bio.bin"

    /** 设备是否具备强生物识别硬件且已设置系统锁屏（用于决定是否展示生物识别入口）。 */
    fun isHardwareReady(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** 是否已封装过主密钥（即用户曾绑定生物识别解锁）。 */
    fun hasWrappedKey(context: Context): Boolean = bioFile(context).exists()

    /** 删除封装文件，关闭生物识别解锁（无需生物认证）。 */
    fun clear(context: Context) {
        bioFile(context).delete()
    }

    private fun bioFile(context: Context) = File(context.filesDir, FILE_NAME)

    private fun getKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun ensureKey() {
        val ks = getKeyStore()
        if (ks.containsAlias(KEY_ALIAS)) return
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 每次使用（含绑定的加密与解锁的解密）都需生物识别认证；生成密钥本身无需认证。
                .setUserAuthenticationRequired(true)
                .build(),
        )
        kg.generateKey()
    }

    private fun bioKey(): SecretKey {
        ensureKey()
        return getKeyStore().getKey(KEY_ALIAS, null) as SecretKey
    }

    /** 绑定用：已认证 cipher 加密主密钥并落盘封装。 */
    fun wrap(context: Context, cipher: Cipher, masterKey: SecretKey) {
        val ct = cipher.doFinal(masterKey.encoded)
        val out = ByteArray(cipher.iv.size + ct.size)
        System.arraycopy(cipher.iv, 0, out, 0, cipher.iv.size)
        System.arraycopy(ct, 0, out, cipher.iv.size, ct.size)
        bioFile(context).writeBytes(out)
    }

    /** 解锁用：从封装文件读密文，用已认证 cipher 解密重建主密钥。 */
    fun unwrap(context: Context, cipher: Cipher): SecretKey {
        val data = bioFile(context).readBytes()
        val ct = data.copyOfRange(GCM_IV_LEN, data.size)
        val raw = cipher.doFinal(ct)
        return SecretKeySpec(raw, "AES")
    }

    /** 准备解锁（解密）用的 cipher：从封装文件读取 IV 并以 DECRYPT_MODE 初始化。 */
    fun prepareDecryptCipher(context: Context): Cipher {
        val data = bioFile(context).readBytes()
        val iv = data.copyOfRange(0, GCM_IV_LEN)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, bioKey(), GCMParameterSpec(GCM_TAG_LEN, iv))
        return cipher
    }

    /** 准备绑定（加密）用的 cipher：ENCRYPT_MODE 初始化，IV 由密钥自身生成。 */
    fun prepareEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, bioKey())
        return cipher
    }
}
