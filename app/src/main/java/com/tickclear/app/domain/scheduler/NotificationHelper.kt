package com.tickclear.app.domain.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.tickclear.app.R

/**
 * 通知渠道管理：按提醒级别分渠道（高/中/低/通用）。
 * 高优先级=声音+震动+灯；低优先级=静默；中优先级=标准。
 */
object NotificationHelper {
    const val CHANNEL_REMINDER = "tickclear.reminder"
    const val CHANNEL_HIGH = "tickclear.reminder.high"
    const val CHANNEL_MID = "tickclear.reminder.mid"
    const val CHANNEL_LOW = "tickclear.reminder.low"
    const val CHANNEL_SILENT = "tickclear.reminder.silent"

    fun channelForLevel(level: String): String = when (level) {
        "high" -> CHANNEL_HIGH
        "low" -> CHANNEL_LOW
        else -> CHANNEL_MID
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(context)
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.notify_channel_reminder),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
        val high = NotificationChannel(
            CHANNEL_HIGH,
            context.getString(R.string.notify_channel_high),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
        }
        val mid = NotificationChannel(
            CHANNEL_MID,
            context.getString(R.string.notify_channel_mid),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
        val low = NotificationChannel(
            CHANNEL_LOW,
            context.getString(R.string.notify_channel_low),
            android.app.NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        // 静音时段专用渠道：IMPORTANCE_MIN，比 LOW 更静默、无通知栏提示（PRD D.2 ch_silent）。
        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(R.string.notify_channel_silent),
            android.app.NotificationManager.IMPORTANCE_MIN,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannels(listOf(reminder, high, mid, low, silent))
    }
}
