package com.tickclear.app.domain.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.di.AppEntryPoint
import com.tickclear.app.domain.log.AppLogger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 自动备份接收器（V2.5）：由 [AutoBackupScheduler] 的 AlarmManager 闹钟触发。
 * 经 [AppEntryPoint] 取依赖（避免 @AndroidEntryPoint 在 Receiver 上的额外约束），
 * 用 [goAsync] 保持进程以完成 IO，完成后写日志并释放。
 */
class AutoBackupReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != AutoBackupScheduler.ACTION_AUTO_BACKUP) return
        val ctx = context ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(ctx.applicationContext, AppEntryPoint::class.java)
                AutoBackupRunner.run(
                    appContext = ctx.applicationContext,
                    baseDir = ctx.filesDir,
                    backupManager = entry.backupManager(),
                    settingsRepository = entry.settingsRepository(),
                )
            } catch (e: Exception) {
                AppLogger.e("AutoBackup", "自动备份失败", e)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
