package com.tickclear.app

import android.app.Application
import com.tickclear.app.data.SecureStore
import com.tickclear.app.di.AppEntryPoint
import com.tickclear.app.domain.log.CrashReporter
import com.tickclear.app.domain.scheduler.NotificationHelper
import com.tickclear.app.domain.scheduler.ReminderScheduler
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
        val seed = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java).seedUseCase()
        scope.launch {
            seed.ensureSeeded()
            ReminderScheduler.rescheduleAll(this@TickClearApplication)
        }
    }
}
