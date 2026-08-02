package com.tickclear.app.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.data.VaultCrypto
import com.tickclear.app.data.VaultMeta
import com.tickclear.app.data.VaultStore
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.VaultEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey
import javax.inject.Inject

private const val VERIFIER_PLAIN = "tickclear-vault-verifier-v1"
private const val MIN_PASS_LEN = 6

enum class VaultMode { SETUP, UNLOCK, LIST, RECOVERY, RECOVERY_NEWPASS }

/**
 * 密码保险箱（V2.9）。[sessionKey] 仅在解锁后驻留内存，离开页面（onCleared）即清除；
 * 落库内容全部经 [VaultCrypto] 加密，本 VM 不持久化任何明文。
 * 找回口令：回答安全问题（慢哈希校验）→ 设置新口令并重新派生密钥、清空旧条目（旧条目无旧口令无法解密）。
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private var sessionKey: SecretKey? = null
    private var recoveryAnswerText: String? = null

    private val _mode = MutableStateFlow(if (VaultStore.exists(appContext)) VaultMode.UNLOCK else VaultMode.SETUP)
    val mode: StateFlow<VaultMode> = _mode.asStateFlow()

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    private val _revealedIds = MutableStateFlow<Set<Long>>(emptySet())
    val revealedIds: StateFlow<Set<Long>> = _revealedIds.asStateFlow()

    private val _recoveryQuestion = MutableStateFlow("")
    val recoveryQuestion: StateFlow<String> = _recoveryQuestion.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _recoveryError = MutableStateFlow<String?>(null)
    val recoveryError: StateFlow<String?> = _recoveryError.asStateFlow()

    // ---------------- 设置 ----------------

    fun setup(pass: String, confirm: String, question: String, answer: String) {
        _setupError.value = validatePass(pass, confirm) ?: validateSetupFields(question, answer)
        if (_setupError.value != null) return

        viewModelScope.launch {
            try {
                val salt = VaultCrypto.randomSalt()
                val answerSalt = VaultCrypto.randomSalt()
                val key = VaultCrypto.deriveKey(pass.toCharArray(), salt)
                val verifier = VaultCrypto.encrypt(key, VERIFIER_PLAIN)
                val answerHash = VaultCrypto.hashAnswer(answer.toCharArray(), answerSalt)
                val emptyBlob = VaultCrypto.encrypt(key, serialize(emptyList()))
                VaultStore.setup(
                    appContext,
                    VaultMeta(salt, verifier, question.trim(), answerHash, emptyBlob, answerSalt),
                )
                sessionKey = key
                _entries.value = emptyList()
                _mode.value = VaultMode.LIST
            } catch (e: Exception) {
                AppLogger.e("VaultVM", "setup failed", e)
                _setupError.value = appContext.getString(R.string.err_unknown)
            }
        }
    }

    // ---------------- 解锁 ----------------

    fun unlock(pass: String) {
        _unlockError.value = null
        val meta = VaultStore.loadMeta(appContext) ?: run {
            _unlockError.value = appContext.getString(R.string.vault_wrong)
            return
        }
        viewModelScope.launch {
            try {
                val key = VaultCrypto.deriveKey(pass.toCharArray(), meta.salt)
                val plain = VaultCrypto.decrypt(key, meta.verifier)
                if (plain != VERIFIER_PLAIN) {
                    _unlockError.value = appContext.getString(R.string.vault_wrong)
                    return@launch
                }
                sessionKey = key
                _entries.value = meta.entriesBlob?.let { decryptEntries(key, it) } ?: emptyList()
                _mode.value = VaultMode.LIST
            } catch (e: Exception) {
                // GCM 校验失败 = 口令错误
                _unlockError.value = appContext.getString(R.string.vault_wrong)
            }
        }
    }

    fun lock() {
        sessionKey = null
        recoveryAnswerText = null
        _entries.value = emptyList()
        _revealedIds.value = emptySet()
        _mode.value = if (VaultStore.exists(appContext)) VaultMode.UNLOCK else VaultMode.SETUP
    }

    // ---------------- 找回口令 ----------------

    fun startRecovery() {
        val meta = VaultStore.loadMeta(appContext)
        _recoveryQuestion.value = meta?.question ?: ""
        _recoveryError.value = null
        _mode.value = VaultMode.RECOVERY
    }

    fun submitRecoveryAnswer(answer: String) {
        val meta = VaultStore.loadMeta(appContext) ?: run {
            _recoveryError.value = appContext.getString(R.string.err_unknown)
            return
        }
        _recoveryError.value = null
        val actual = VaultCrypto.hashAnswer(answer.toCharArray(), meta.answerSalt)
        if (actual == meta.answerHash) {
            recoveryAnswerText = answer
            _mode.value = VaultMode.RECOVERY_NEWPASS
        } else {
            _recoveryError.value = appContext.getString(R.string.vault_recovery_wrong)
        }
    }

    fun submitRecoveryNewPass(pass: String, confirm: String) {
        val answer = recoveryAnswerText ?: run {
            _recoveryError.value = appContext.getString(R.string.err_unknown)
            return
        }
        _recoveryError.value = validatePass(pass, confirm)
        if (_recoveryError.value != null) return

        viewModelScope.launch {
            try {
                val salt = VaultCrypto.randomSalt()
                val answerSalt = VaultCrypto.randomSalt()
                val key = VaultCrypto.deriveKey(pass.toCharArray(), salt)
                val verifier = VaultCrypto.encrypt(key, VERIFIER_PLAIN)
                val answerHash = VaultCrypto.hashAnswer(answer.toCharArray(), answerSalt)
                val emptyBlob = VaultCrypto.encrypt(key, serialize(emptyList()))
                VaultStore.rekey(appContext, salt, verifier, answerHash, emptyBlob, answerSalt)
                recoveryAnswerText = null
                sessionKey = key
                _entries.value = emptyList()
                _revealedIds.value = emptySet()
                _mode.value = VaultMode.LIST
            } catch (e: Exception) {
                AppLogger.e("VaultVM", "recovery rekey failed", e)
                _recoveryError.value = appContext.getString(R.string.err_unknown)
            }
        }
    }

    // ---------------- 条目增删改 ----------------

    fun upsertEntry(entry: VaultEntry) {
        val key = sessionKey ?: return
        val e = if (entry.id <= 0) entry.copy(id = nextId()) else entry
        val list = _entries.value.toMutableList()
        val idx = list.indexOfFirst { it.id == e.id }
        if (idx >= 0) list[idx] = e else list.add(0, e)
        _entries.value = list
        persistEntries(key, list)
    }

    private fun nextId(): Long = (_entries.value.maxOfOrNull { it.id } ?: 0) + 1

    fun deleteEntry(id: Long) {
        val key = sessionKey ?: return
        val list = _entries.value.filter { it.id != id }
        _entries.value = list
        _revealedIds.value = _revealedIds.value - id
        persistEntries(key, list)
    }

    fun toggleReveal(id: Long) {
        val set = _revealedIds.value.toMutableSet()
        if (!set.add(id)) set.remove(id)
        _revealedIds.value = set
    }

    fun clearErrors() {
        _setupError.value = null
        _unlockError.value = null
        _recoveryError.value = null
    }

    private fun persistEntries(key: SecretKey, list: List<VaultEntry>) {
        runCatching {
            val blob = VaultCrypto.encrypt(key, serialize(list))
            VaultStore.updateEntries(appContext, blob)
        }.onFailure { e -> AppLogger.e("VaultVM", "persist entries failed", e) }
    }

    private fun decryptEntries(key: SecretKey, blob: String): List<VaultEntry> =
        runCatching { deserialize(VaultCrypto.decrypt(key, blob)) }.getOrDefault(emptyList())

    private fun validatePass(pass: String, confirm: String): String? {
        val ctx = appContext
        if (pass.length < MIN_PASS_LEN) return ctx.getString(R.string.vault_weak)
        if (pass != confirm) return ctx.getString(R.string.vault_password_mismatch)
        return null
    }

    private fun validateSetupFields(question: String, answer: String): String? {
        if (question.trim().isEmpty() || answer.trim().isEmpty()) {
            return appContext.getString(R.string.vault_recovery_required)
        }
        return null
    }

    private fun serialize(list: List<VaultEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("address", e.address)
                put("username", e.username)
                put("password", e.password)
                put("note", e.note)
            }
            arr.put(o)
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<VaultEntry> {
        val arr = JSONArray(json)
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

    override fun onCleared() {
        super.onCleared()
        sessionKey = null
        recoveryAnswerText = null
    }
}
