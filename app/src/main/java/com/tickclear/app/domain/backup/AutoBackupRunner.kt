package com.tickclear.app.domain.backup

import android.content.Context
import com.tickclear.app.data.FamilyPointsBackup
import com.tickclear.app.data.VaultBackup
import com.tickclear.app.data.VaultCrypto
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 自动备份执行体（V2.5/V2.6，V2.9++ 升级为全量结构）：导出加密 JSON 到应用私有目录 `filesDir/backups/`，
 * 保留最近 [MAX_COPIES] 份（按文件名排序轮转），回写最近备份时间并记录日志。
 *
 * - 写入私有目录而非 SAF：后台定时任务无法弹出系统选择器，私有目录在重装前持久、
 *   且无需存储权限，符合零依赖与隐私定位。
 * - 文件扩展名 `.tcbackup`，与手动明文 `.json` 区分；内容经 [BackupCrypto] 加密。
 * - 全量结构 = core(任务/习惯) + settings(系统/助手/工具配置) + vault(加密元数据) + familyPoints(家庭积分)，
 *   与「备份导出」工具一致，保证任意一份备份都能完整恢复。
 */
object AutoBackupRunner {

    private const val MAX_COPIES = 7
    private const val DIR = "backups"
    private const val EXT = ".tcbackup"

    suspend fun run(
        appContext: Context,
        baseDir: File,
        backupManager: BackupManager,
        settingsRepository: SettingsRepository,
    ) {
        val dir = File(baseDir, DIR).apply { mkdirs() }

        // 组装与「备份导出」工具一致的全量嵌套结构。
        val core = JSONObject(backupManager.exportToJson())
        val root = JSONObject().apply {
            put("app", core.optString("app", "TickClear"))
            put("schemaVersion", core.optInt("schemaVersion", 1))
            put("exportedAt", System.currentTimeMillis())
            put("core", core)
            put("settings", JSONObject(settingsRepository.exportSettingsJson()))

            // 保险箱始终含加密元数据（安全）；自动备份不请求明文。
            val vb = VaultBackup.export(appContext, null)
            val vaultObj = JSONObject()
            if (vb.meta != null) {
                vaultObj.put("initialized", true)
                vaultObj.put("salt", VaultCrypto.bytesToB64(vb.meta.salt))
                vaultObj.put("verifier", vb.meta.verifier)
                vaultObj.put("question", vb.meta.question)
                vaultObj.put("answerHash", vb.meta.answerHash)
                vaultObj.put("answerSalt", VaultCrypto.bytesToB64(vb.meta.answerSalt))
                vaultObj.put("entriesBlob", vb.meta.entriesBlob ?: JSONObject.NULL)
            } else {
                vaultObj.put("initialized", false)
            }
            put("vault", vaultObj)

            // 家庭积分存于独立 SharedPreferences，一并纳入自动备份。
            put("familyPoints", FamilyPointsBackup.export(appContext))
        }

        val bytes = BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8))

        val stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val file = File(dir, "auto_backup_$stamp$EXT")
        file.writeBytes(bytes)

        // 轮转：保留最新 MAX_COPIES 份。
        dir.listFiles { f -> f.name.endsWith(EXT) }
            ?.sortedBy { it.name }
            ?.dropLast(MAX_COPIES)
            ?.forEach { it.delete() }

        settingsRepository.setLastAutoBackupAt(System.currentTimeMillis())

        // V2.23 备份自愈校验：解密刚写入的字节并校验结构，回写健康状态供设置页回显。
        // 校验失败不影响已落盘的备份（仅健康状态标记为 CORRUPT，提示用户重备）。
        runCatching {
            val json = BackupCrypto.decrypt(bytes).toString(Charsets.UTF_8)
            settingsRepository.setLastBackupHealth(backupManager.validateBackupJson(json))
        }.onFailure { AppLogger.w("AutoBackup", "备份自检失败（不影响已写入的备份）", it) }

        AppLogger.i("AutoBackup", "已写入 ${file.name}（${bytes.size} 字节，保留 ${dir.listFiles { f -> f.name.endsWith(EXT) }?.size ?: 0} 份）")
    }
}
