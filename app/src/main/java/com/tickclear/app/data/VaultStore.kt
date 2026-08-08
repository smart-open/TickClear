@file:Suppress("DEPRECATION")
// 与 SecureStore 同理：security-crypto 1.1.0 已弃用但零新依赖红线要求沿用，文件级抑制告警。

package com.tickclear.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Base64

/**
 * 密码保险箱元数据持久化（V2.9）。仅存「加密后的密文 + 口令派生所需随机盐 + 验证器」，
 * 不存任何明文条目。条目密文本身见 [VaultViewModel] 经 [VaultCrypto] 处理后写入 [KEY_ENTRIES]。
 */
object VaultStore {

    private const val PREFS = "tickclear_vault"
    private const val KEY_SALT = "vault_salt"
    private const val KEY_VERIFIER = "vault_verifier"
    private const val KEY_QUESTION = "vault_question"
    private const val KEY_ANSWER_HASH = "vault_answer_hash"
    private const val KEY_ANSWER_SALT = "vault_answer_salt"
    private const val KEY_ENTRIES = "vault_entries"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** 是否已设置主口令（存在验证器即视为已初始化）。 */
    fun exists(context: Context): Boolean = prefs(context).contains(KEY_VERIFIER)

    /** 读出全部元数据；未初始化返回 null。 */
    fun loadMeta(context: Context): VaultMeta? {
        val p = prefs(context)
        val verifier = p.getString(KEY_VERIFIER, null) ?: return null
        val saltB64 = p.getString(KEY_SALT, null) ?: return null
        val salt = VaultCrypto.b64ToBytes(saltB64)
        // 旧版仅存主盐；答案盐缺失时回退主盐以保证兼容。
        val answerSalt = p.getString(KEY_ANSWER_SALT, null)?.let { VaultCrypto.b64ToBytes(it) } ?: salt
        return VaultMeta(
            salt = salt,
            verifier = verifier,
            question = p.getString(KEY_QUESTION, "") ?: "",
            answerHash = p.getString(KEY_ANSWER_HASH, "") ?: "",
            entriesBlob = p.getString(KEY_ENTRIES, null),
            answerSalt = answerSalt,
        )
    }

    /** 首次设置：写入盐 / 验证器 / 安全问题 / 答案哈希(独立盐) / 空条目 blob。 */
    fun setup(context: Context, meta: VaultMeta) {
        prefs(context).edit().apply {
            putString(KEY_SALT, VaultCrypto.bytesToB64(meta.salt))
            putString(KEY_VERIFIER, meta.verifier)
            putString(KEY_QUESTION, meta.question)
            putString(KEY_ANSWER_HASH, meta.answerHash)
            putString(KEY_ANSWER_SALT, VaultCrypto.bytesToB64(meta.answerSalt))
            putString(KEY_ENTRIES, meta.entriesBlob)
            apply()
        }
    }

    /** 解锁后每次条目变更：仅覆盖加密条目 blob。 */
    fun updateEntries(context: Context, entriesBlob: String) {
        prefs(context).edit().putString(KEY_ENTRIES, entriesBlob).apply()
    }

    /**
     * 从全量备份恢复元数据（V2.9++ 导入）。密文 blob 原样写回，保险箱仍保持「锁定」，
     * 用户在新设备凭原主口令解锁即可取回条目——无需也不持有明文口令。
     * 与 [setup] 区别：恢复不假定「首次」，直接覆盖全部元数据。
     */
    fun restoreMeta(context: Context, meta: VaultMeta) {
        prefs(context).edit().apply {
            putString(KEY_SALT, VaultCrypto.bytesToB64(meta.salt))
            putString(KEY_VERIFIER, meta.verifier)
            putString(KEY_QUESTION, meta.question)
            putString(KEY_ANSWER_HASH, meta.answerHash)
            putString(KEY_ANSWER_SALT, VaultCrypto.bytesToB64(meta.answerSalt))
            putString(KEY_ENTRIES, meta.entriesBlob)
            apply()
        }
    }

    /**
     * 通过安全问题重置口令：覆盖盐 / 验证器 / 答案哈希 / 条目（清空），保留原安全问题。
     * 旧条目因无旧口令无法解密，按约定直接清空、不可恢复。
     */
    fun rekey(context: Context, salt: ByteArray, verifier: String, answerHash: String, entriesBlob: String, answerSalt: ByteArray) {
        prefs(context).edit().apply {
            putString(KEY_SALT, VaultCrypto.bytesToB64(salt))
            putString(KEY_VERIFIER, verifier)
            putString(KEY_ANSWER_HASH, answerHash)
            putString(KEY_ANSWER_SALT, VaultCrypto.bytesToB64(answerSalt))
            putString(KEY_ENTRIES, entriesBlob)
            apply()
        }
    }
}

data class VaultMeta(
    val salt: ByteArray,
    val verifier: String,
    val question: String,
    val answerHash: String,
    val entriesBlob: String?,
    val answerSalt: ByteArray,
)
