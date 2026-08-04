package com.tickclear.app.domain.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
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
    private const val CHANNEL_VERSION = "v4"

    const val CHANNEL_REMINDER = "tickclear.reminder.$CHANNEL_VERSION"
    const val CHANNEL_HIGH = "tickclear.reminder.high.$CHANNEL_VERSION"
    const val CHANNEL_MID = "tickclear.reminder.mid.$CHANNEL_VERSION"

    /**
     * 中优先级「静音变体」：用户在设置里关闭了全局「声音」时使用。
     * Android 8+ 声音由渠道决定、无法在单条通知上关掉，因此只能另建一条无声但保留震动与
     * 抬头展示的渠道 —— 这样「声音」开关才真正生效（此前该开关只打日志，是死设置）。
     */
    const val CHANNEL_MID_MUTED = "tickclear.reminder.mid.muted.$CHANNEL_VERSION"
    const val CHANNEL_LOW = "tickclear.reminder.low.$CHANNEL_VERSION"
    const val CHANNEL_SILENT = "tickclear.reminder.silent.$CHANNEL_VERSION"

    // ── 工具箱间隔提醒渠道（V2.9）：喝水 / 休息。带版本后缀，便于统一清理。 ──
    const val CHANNEL_WATER = "tickclear.tools.water.$CHANNEL_VERSION"
    const val CHANNEL_REST = "tickclear.tools.rest.$CHANNEL_VERSION"
    const val CHANNEL_EYECARE = "tickclear.tools.eyecare.$CHANNEL_VERSION"
    const val CHANNEL_NAP = "tickclear.tools.nap.$CHANNEL_VERSION"
    const val CHANNEL_EXPIRY = "tickclear.tools.expiry.$CHANNEL_VERSION"
    const val CHANNEL_HEARING = "tickclear.tools.hearing.$CHANNEL_VERSION"
    const val CHANNEL_CLOCK = "tickclear.tools.clock.$CHANNEL_VERSION"
    /** 到站提醒渠道（V2.9++）。 */
    const val CHANNEL_ARRIVAL = "tickclear.tools.arrival.$CHANNEL_VERSION"

    /** 历史渠道 ID（升级清理用）：无后缀初版 + 各历史版本后缀。 */
    private val LEGACY_CHANNEL_IDS = listOf(
        "tickclear.reminder",
        "tickclear.reminder.high",
        "tickclear.reminder.mid",
        "tickclear.reminder.low",
        "tickclear.reminder.silent",
        // v3：中优先级渠道漏配震动（enableVibration 默认 false），中优先级提醒只响不震。
        "tickclear.reminder.v3",
        "tickclear.reminder.high.v3",
        "tickclear.reminder.mid.v3",
        "tickclear.reminder.low.v3",
        "tickclear.reminder.silent.v3",
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
        ).apply {
            // V2.8X 修复：中优先级渠道此前未显式开启震动。Android 8+ 渠道 enableVibration 默认 false，
            // 且 NotificationCompat.DEFAULT_VIBRATE 在 O+ 被忽略 —— 结果「中」提醒只响铃不震动，
            // 与产品定义（中=标准提醒，声音+震动）不符。此处显式开启并给出较轻的震动节奏。
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        val midMuted = NotificationChannel(
            CHANNEL_MID_MUTED,
            context.getString(R.string.notify_channel_mid_muted),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
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
        // 工具箱间隔提醒渠道：标准重要性 + 震动，点击仅打开 App。
        val water = NotificationChannel(
            CHANNEL_WATER,
            context.getString(R.string.channel_water_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_water_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        val rest = NotificationChannel(
            CHANNEL_REST,
            context.getString(R.string.channel_rest_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_rest_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        val eyecare = NotificationChannel(
            CHANNEL_EYECARE,
            context.getString(R.string.channel_eyecare_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_eyecare_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        val nap = NotificationChannel(
            CHANNEL_NAP,
            context.getString(R.string.channel_nap_name),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_nap_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
        }
        val expiry = NotificationChannel(
            CHANNEL_EXPIRY,
            context.getString(R.string.channel_expiry_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_expiry_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        val hearing = NotificationChannel(
            CHANNEL_HEARING,
            context.getString(R.string.channel_hearing_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_hearing_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
    // 悬浮时钟渠道：低重要性、常驻、无声无震（后台显示时间用，不扰民）。
    val clock = NotificationChannel(
        CHANNEL_CLOCK,
        context.getString(R.string.channel_clock_name),
        android.app.NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = context.getString(R.string.channel_clock_desc)
        setShowBadge(false)
    }
    // 到站提醒渠道：标准重要性 + 震动（靠近站点震动 + 抬头通知）。
    val arrival = NotificationChannel(
        CHANNEL_ARRIVAL,
        context.getString(R.string.channel_arrival_name),
        android.app.NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.channel_arrival_desc)
        enableLights(true)
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 400, 200, 400)
    }
    manager.createNotificationChannels(listOf(reminder, high, mid, midMuted, low, silent, water, rest, eyecare, nap, expiry, hearing, clock, arrival))
    }

    /**
     * 工具箱间隔提醒通知（V2.9）：喝水 / 休息。点击打开 App 工具箱。
     * 通知 id 按类型固定，避免每次提醒堆叠多条。
     */
    fun showIntervalReminder(context: Context, type: com.tickclear.app.domain.scheduler.IntervalType) {
        val (channelId, titleRes, textRes, notifyId) = when (type) {
            com.tickclear.app.domain.scheduler.IntervalType.WATER ->
                Quad(CHANNEL_WATER, R.string.interval_water_title, R.string.interval_water_text, 9201)
            com.tickclear.app.domain.scheduler.IntervalType.REST ->
                Quad(CHANNEL_REST, R.string.interval_rest_title, R.string.interval_rest_text, 9202)
            com.tickclear.app.domain.scheduler.IntervalType.EYECARE ->
                Quad(CHANNEL_EYECARE, R.string.interval_eyecare_title, R.string.interval_eyecare_text, 9203)
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notifyId, notification) }
    }

    /** 午休小憩唤醒通知（V2.9++）。点击仅打开 App。 */
    fun showNapNotification(context: Context) {
        val notifyId = 9301
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NAP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.nap_notification_title))
            .setContentText(context.getString(R.string.nap_notification_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notifyId, notification) }
    }

    /** 内部四元组（避免引入额外依赖）。 */
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** 到期提醒通知（V2.9++）。点击仅打开 App。固定 id，避免重复堆叠。 */
    fun showExpiryNotification(context: Context, title: String) {
        val notifyId = 9351
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.expiry_notification_title, title))
            .setContentText(context.getString(R.string.expiry_notification_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notifyId, notification) }
    }

    /** 听力保护提醒（V2.9++）：音量过高 / 佩戴过久时弹通知。点击仅打开 App。 */
    fun showHearingNotification(context: Context, reason: String) {
        val notifyId = 9361
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_HEARING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.hearing_notification_title))
            .setContentText(reason)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notifyId, notification) }
    }

    /** 到站提醒通知（V2.9++）：靠近站点时震动 + 该抬头通知。点击仅打开 App。固定 id 避免堆叠。 */
    fun showArrivalNotification(context: Context, stationName: String) {
        val notifyId = 9401
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ARRIVAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.arrival_notify_title))
            .setContentText(context.getString(R.string.arrival_notify_text, stationName))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notifyId, notification) }
    }
}
