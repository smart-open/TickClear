package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.ExpiryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 到期提醒接收器（V2.9++）：到点弹通知；若为每年重复项，落库顺延一年并续排下一年闹钟。
 * 必须走 goAsync + 协程作用域（挂起上下文）才能调用 suspend 仓库方法，
 * 否则 runCatching 块内调 suspend 会编译失败（见项目红线）。
 */
class ExpiryReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ExpiryEntryPoint {
        fun expiryRepository(): ExpiryRepository
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ExpiryScheduler.ACTION_EXPIRY) return
        val id = intent.getLongExtra(ExpiryScheduler.EXTRA_EXPIRY_ID, -1L)
        if (id <= 0 || context == null) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = EntryPointAccessors
                    .fromApplication(context.applicationContext, ExpiryEntryPoint::class.java)
                    .expiryRepository()
                val item = repo.getById(id)
                if (item == null) {
                    // 已删除：撤销可能残留的闹钟
                    ExpiryScheduler.cancel(context, id)
                    return@launch
                }
                // 弹通知（标题即时读取，不依赖挂起）
                NotificationHelper.showExpiryNotification(context, item.title)
                // 每年重复：顺延一年并续排
                if (item.recurring) {
                    val next = item.copy(
                        expireEpochDay = LocalDate.ofEpochDay(item.expireEpochDay)
                            .plusYears(1)
                            .toEpochDay(),
                    )
                    repo.update(next)
                    ExpiryScheduler.schedule(context, next)
                }
            } catch (e: Exception) {
                AppLogger.e("ExpiryReceiver", "处理到期提醒失败 id=$id: ${e.message}")
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
