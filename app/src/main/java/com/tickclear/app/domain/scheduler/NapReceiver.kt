package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.tools.NapPlaybackService

/**
 * 午休小憩接收器（V2.9++）：由 [NapScheduler] 的一次性闹钟触发，弹出唤醒通知。
 */
class NapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching { NotificationHelper.showNapNotification(context) }
            .onFailure { AppLogger.e("NapReceiver", "发通知失败：${it.message}") }
        // 唤醒时若白噪音仍在播放（尚未渐隐完），停止前台播放服务。
        runCatching { context.startService(NapPlaybackService.stopIntent(context)) }
            .onFailure { AppLogger.e("NapReceiver", "停白噪音失败：${it.message}") }
    }
}
