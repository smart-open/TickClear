package com.tickclear.app.ui.tools

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.data.VaultBackup
import com.tickclear.app.data.VaultCrypto
import com.tickclear.app.data.VaultStore
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.backup.BackupManager
import com.tickclear.app.domain.log.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 备份导出（V2.9++）：一键导出「工具箱设置 + 密码保险箱 + 习惯/任务记录」为单个 JSON 文件。
 * - 复用 [BackupManager] 导出任务/习惯/打卡/勋章（零依赖 org.json）；
 * - 追加 DataStore 全部设置（[SettingsRepository.exportSettingsJson]）；
 * - 追加密码保险箱：始终含加密元数据（安全）；若用户解锁并选择明文，则额外含可读条目。
 * 文件写入应用私有外部目录 filesDir/backups，经 FileProvider 授权分享。
 */
@HiltViewModel
class BackupExportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _result = MutableStateFlow<BackupResult?>(null)
    val result: StateFlow<BackupResult?> = _result.asStateFlow()

    private val _vaultExists = MutableStateFlow(false)
    val vaultExists: StateFlow<Boolean> = _vaultExists.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _error.asSharedFlow()

    init {
        _vaultExists.value = VaultStore.exists(appContext)
    }

    data class BackupResult(
        val file: File,
        val uri: Uri?,
        val sizeKb: Long,
        val summary: String,
        val vaultPlaintext: Boolean,
    )

    fun export(passphrase: String?, includePlaintext: Boolean) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject()
                val core = JSONObject(backupManager.exportToJson())
                root.put("app", core.optString("app", "TickClear"))
                root.put("schemaVersion", core.optInt("schemaVersion", 1))
                root.put("exportedAt", System.currentTimeMillis())
                root.put("core", core)
                root.put("settings", JSONObject(settingsRepository.exportSettingsJson()))

                val vb = VaultBackup.export(appContext, if (includePlaintext) passphrase else null)
                val vaultObj = JSONObject()
                if (vb.meta != null) {
                    vaultObj.put("initialized", true)
                    vaultObj.put("salt", VaultCrypto.bytesToB64(vb.meta.salt))
                    vaultObj.put("verifier", vb.meta.verifier)
                    vaultObj.put("question", vb.meta.question)
                    vaultObj.put("answerHash", vb.meta.answerHash)
                    vaultObj.put("answerSalt", VaultCrypto.bytesToB64(vb.meta.answerSalt))
                    vaultObj.put("entriesBlob", vb.meta.entriesBlob ?: JSONObject.NULL)
                    if (vb.wrongPass) {
                        vaultObj.put("unlockError", true)
                    } else if (includePlaintext && vb.entries != null) {
                        val arr = JSONArray()
                        vb.entries.forEach { e ->
                            arr.put(JSONObject().apply {
                                put("id", e.id); put("title", e.title); put("address", e.address)
                                put("username", e.username); put("password", e.password); put("note", e.note)
                            })
                        }
                        vaultObj.put("entries", arr)
                    }
                } else {
                    vaultObj.put("initialized", false)
                }
                root.put("vault", vaultObj)

                val json = root.toString(2)
                val dir = File(appContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val file = File(dir, "tickclear_backup_$stamp.json")
                file.writeText(json, Charsets.UTF_8)
                val uri = runCatching {
                    FileProvider.getUriForFile(appContext, "com.tickclear.app.fileprovider", file)
                }.getOrNull()
                val sizeKb = file.length() / 1024
                val vaultPlain = includePlaintext && vb.entries != null && !vb.wrongPass
                val habits = core.optJSONArray("habits")?.length() ?: 0
                val tasks = core.optJSONArray("tasks")?.length() ?: 0
                _result.value = BackupResult(
                    file = file,
                    uri = uri,
                    sizeKb = sizeKb,
                    summary = appContext.getString(R.string.backup_summary, habits, tasks),
                    vaultPlaintext = vaultPlain,
                )
            } catch (e: Exception) {
                AppLogger.e("BackupExport", "export failed", e)
                _error.tryEmit(appContext.getString(R.string.backup_export_fail))
            } finally {
                _exporting.value = false
            }
        }
    }
}
