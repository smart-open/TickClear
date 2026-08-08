package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.R

/**
 * 倒计时提醒接收器（V2.9++）：由 [CountdownScheduler] 的精确闹钟触发，
 * 读取事件名与剩余天数，弹出渠道化通知。
 */
class CountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != CountdownScheduler.ACTION_COUNTDOWN) return
        val ctx = context ?: return
        val name = intent.getStringExtra(CountdownScheduler.EXTRA_NAME) ?: return
        val daysLeft = intent.getIntExtra(CountdownScheduler.EXTRA_DAYS_LEFT, 0)
        val text = if (daysLeft <= 0) {
            ctx.getString(R.string.countdown_notif_today)
        } else {
            ctx.getString(R.string.countdown_notif_days, daysLeft)
        }
        NotificationHelper.showCountdownNotification(ctx, name, text)
    }
}
