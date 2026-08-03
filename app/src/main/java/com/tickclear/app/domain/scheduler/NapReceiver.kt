package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.domain.log.AppLogger

/**
 * 午休小憩接收器（V2.9++）：由 [NapScheduler] 的一次性闹钟触发，弹出唤醒通知。
 */
class NapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching { NotificationHelper.showNapNotification(context) }
            .onFailure { AppLogger.e("NapReceiver", "发通知失败：${it.message}") }
    }
}
