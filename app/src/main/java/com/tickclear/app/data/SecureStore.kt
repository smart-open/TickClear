package com.tickclear.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * 敏感数据安全存储（Keystore + EncryptedSharedPreferences）。
 * - SQLCipher 数据库口令
 * - ASR / LLM 服务商密钥（Phase 5）
 * 注意：口令丢失 = 加密库不可解密，需在关于页提示用户。
 */
object SecureStore {

    private const val PREFS = "tickclear_secure"
    private const val KEY_DB_PASS = "db_passphrase"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** 首次调用惰性生成并持久化 SQLCipher 口令。 */
    fun getDbPassphrase(context: Context): String {
        val p = prefs(context)
        var pass = p.getString(KEY_DB_PASS, null)
        if (pass.isNullOrEmpty()) {
            pass = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            // L1：必须 commit() 同步落盘，避免首启「建库读口令」与「写口令」的竞态丢库。
            p.edit().putString(KEY_DB_PASS, pass).commit()
        }
        return pass
    }

    fun getSecret(context: Context, key: String): String? =
        prefs(context).getString(key, null)

    fun putSecret(context: Context, key: String, value: String) =
        prefs(context).edit().putString(key, value).apply()

    fun clearSecret(context: Context, key: String) =
        prefs(context).edit().remove(key).apply()
}
