package com.tickclear.app.domain.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tickclear.app.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 自动备份排程（V2.5）：框架 [AlarmManager] 每日「近似重复」触发，零新依赖（不引入 WorkManager）。
 *
 * - 默认 03:00 附近触发（[AlarmManager.RTC] 不强制唤醒，设备唤醒后执行，省电）。
 * - 开关由 [com.tickclear.app.domain.repository.SettingsRepository.autoBackupEnabled] 控制：
 *   开启即排程，关闭即取消。开机后由 [com.tickclear.app.domain.scheduler.BootReceiver] 重新同步。
 */
object AutoBackupScheduler {

    const val ACTION_AUTO_BACKUP = "com.tickclear.app.AUTO_BACKUP"
    private const val REQUEST_CODE = 0xAB01
    private const val HOUR_OF_DAY = 3

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoBackupReceiver::class.java).apply { action = ACTION_AUTO_BACKUP }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 依据当前开关状态同步闹钟（供设置页开关与开机广播调用，须于协程内调用）。 */
    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(appContext, AppEntryPoint::class.java)
        val enabled = runCatching { entry.settingsRepository().autoBackupEnabled.first() }.getOrDefault(false)
        if (enabled) schedule(appContext) else cancel(appContext)
    }

    private fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC,
            nextTriggerMillis(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    private fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    /** 下一次触发时刻：今日 03:00，若已过则明日 03:00。 */
    private fun nextTriggerMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
