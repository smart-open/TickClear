package com.tickclear.app

import android.app.Application
import com.tickclear.app.data.SecureStore
import com.tickclear.app.di.AppEntryPoint
import com.tickclear.app.domain.backup.AutoBackupScheduler
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.log.CrashReporter
import com.tickclear.app.domain.scheduler.NotificationHelper
import com.tickclear.app.domain.scheduler.ReminderScheduler
import com.tickclear.app.domain.scheduler.HabitReminderScheduler
import com.tickclear.app.domain.scheduler.RecycleBinScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TickClearApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // V2.3：安装崩溃遥测（预载历史崩溃 + 全局未捕获异常处理器）。
        CrashReporter.install(this)
        // 预热 SQLCipher 口令（首次生成并持久化到 Keystore）
        SecureStore.getDbPassphrase(this)
        // 通知渠道（提醒系统）
        NotificationHelper.createChannels(this)
        // 调度每日回收站清理
        RecycleBinScheduler.schedule(this)
        // 首次启动注入示例数据 + 重排今日提醒
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val seed = entryPoint.seedUseCase()
        val settings = entryPoint.settingsRepository()
        scope.launch {
            // V2.8X：调试日志开关持续同步到 AppLogger（启动即生效 + 设置页切换实时生效）。
            settings.debugLogEnabled.collect { AppLogger.setDebugEnabled(it) }
        }
        scope.launch {
            // V2.5：同步自动备份闹钟（开关开启才排程）。
            AutoBackupScheduler.sync(this@TickClearApplication)
            seed.ensureSeeded()
            // 排程失败（如缺少精确闹钟权限被系统拒绝）绝不应阻断启动：各自吞掉异常并记录，App 照常打开。
            runCatching { ReminderScheduler.rescheduleAll(this@TickClearApplication) }
                .onFailure { AppLogger.e("TickClearApplication", "rescheduleAll(tasks) 失败：${it.message}") }
            runCatching { HabitReminderScheduler.rescheduleAll(this@TickClearApplication) }
                .onFailure { AppLogger.e("TickClearApplication", "rescheduleAll(habits) 失败：${it.message}") }
        }
    }
}
