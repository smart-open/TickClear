package com.tickclear.app.domain.backup

import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 自动备份执行体（V2.5/V2.6）：导出加密 JSON 到应用私有目录 `filesDir/backups/`，
 * 保留最近 [MAX_COPIES] 份（按文件名排序轮转），回写最近备份时间并记录日志。
 *
 * - 写入私有目录而非 SAF：后台定时任务无法弹出系统选择器，私有目录在重装前持久、
 *   且无需存储权限，符合零依赖与隐私定位。
 * - 文件扩展名 `.tcbackup`，与手动明文 `.json` 区分；内容经 [BackupCrypto] 加密。
 */
object AutoBackupRunner {

    private const val MAX_COPIES = 7
    private const val DIR = "backups"
    private const val EXT = ".tcbackup"

    suspend fun run(
        baseDir: File,
        backupManager: BackupManager,
        settingsRepository: SettingsRepository,
    ) {
        val dir = File(baseDir, DIR).apply { mkdirs() }
        val bytes = backupManager.exportEncrypted()
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
