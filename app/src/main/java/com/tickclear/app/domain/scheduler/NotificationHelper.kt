package com.tickclear.app.domain.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.tickclear.app.R

/**
 * 通知渠道管理：按提醒级别分渠道（高/中/低/通用）。
 * 高优先级=声音+震动+灯；低优先级=静默；中优先级=标准。
 */
object NotificationHelper {
    /**
     * 渠道版本后缀：当渠道的音/震/重要性需要调整时递增，强制创建全新渠道。
     * Android 8+ 一旦渠道被创建便无法就地升级其音/震/重要性（重复 createNotificationChannel 是空操作），
     * 旧版静音/无震渠道会永久残留并使新通知静默——历史上正是"测试通知有声却设备不响"的根因。
     * 切换后缀即生成全新渠道（带正确音/震），旧 ID 在 createChannels 中一并清理。
     */
    private const val CHANNEL_VERSION = "v3"

    const val CHANNEL_REMINDER = "tickclear.reminder.$CHANNEL_VERSION"
    const val CHANNEL_HIGH = "tickclear.reminder.high.$CHANNEL_VERSION"
    const val CHANNEL_MID = "tickclear.reminder.mid.$CHANNEL_VERSION"
    const val CHANNEL_LOW = "tickclear.reminder.low.$CHANNEL_VERSION"
    const val CHANNEL_SILENT = "tickclear.reminder.silent.$CHANNEL_VERSION"

    /** 旧版未带版本后缀的渠道 ID（升级清理用）。 */
    private val LEGACY_CHANNEL_IDS = listOf(
        "tickclear.reminder",
        "tickclear.reminder.high",
        "tickclear.reminder.mid",
        "tickclear.reminder.low",
        "tickclear.reminder.silent",
    )

    fun channelForLevel(level: String): String = when (level) {
        "high" -> CHANNEL_HIGH
        "low" -> CHANNEL_LOW
        else -> CHANNEL_MID
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 清理旧版（无版本后缀）渠道：其音/震/重要性无法就地升级，残留会污染设备、
        // 使新通知落在旧静音渠道上静默。删除后下方以新 ID 重建带正确音/震的渠道。
        for (legacy in LEGACY_CHANNEL_IDS) {
            runCatching { manager.deleteNotificationChannel(legacy) }
        }
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
            // 高优先级提醒在「勿扰模式」下仍响铃+震动（需用户在系统设置授予「通知优先级访问」权限，见调试页）。
            setBypassDnd(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            // V2.63：高优先级提醒采用内置开源 CC0 提示音（res/raw/notify_chime.wav），零版权风险。
            val chime = Uri.parse(
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.notify_chime}",
            )
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            setSound(chime, attrs)
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
