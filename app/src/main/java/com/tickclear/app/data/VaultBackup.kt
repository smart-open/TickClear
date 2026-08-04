package com.tickclear.app.data

import android.content.Context
import com.tickclear.app.domain.model.VaultEntry
import com.tickclear.app.ui.tools.VAULT_VERIFIER_PLAIN
import org.json.JSONArray
import javax.crypto.SecretKey

/**
 * 密码保险箱备份导出辅助（V2.9++，零新依赖）。
 * - 始终可导出加密元数据（[VaultMeta]：盐/验证器/安全问题/加密条目 blob），落盘即密文、安全；
 * - 若用户在本工具内输入正确主口令并选择「解密并包含明文条目」，则 [export] 解密出可读条目，
 *   便于用户在备份中直接查看；明文会写入备份文件，需用户自行妥善保管备份文件。
 */
object VaultBackup {

    data class Result(
        /** 未设置保险箱时为 null。 */
        val meta: VaultMeta?,
        /** 已解密的可读条目；未解锁/未提供口令时为 null。 */
        val entries: List<VaultEntry>?,
        /** 口令错误标记；为 true 时 entries 不可信。 */
        val wrongPass: Boolean,
    )

    fun export(context: Context, passphrase: String?): Result {
        val meta = VaultStore.loadMeta(context) ?: return Result(null, null, false)
        if (passphrase == null) return Result(meta, null, false)
        return try {
            val key: SecretKey = VaultCrypto.deriveKey(passphrase.toCharArray(), meta.salt)
            val plain = VaultCrypto.decrypt(key, meta.verifier)
            if (plain != VAULT_VERIFIER_PLAIN) return Result(meta, null, true)
            val entries = meta.entriesBlob?.let { decryptEntries(key, it) } ?: emptyList()
            Result(meta, entries, false)
        } catch (e: Exception) {
            // GCM 校验失败 / 派生异常 = 口令错误
            Result(meta, null, true)
        }
    }

    private fun decryptEntries(key: SecretKey, blob: String): List<VaultEntry> {
        val arr = JSONArray(blob)
        val out = mutableListOf<VaultEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VaultEntry(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    address = o.optString("address"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    note = o.optString("note"),
                ),
            )
        }
        return out
    }
}
