package com.tickclear.app.domain.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 习惯提醒广播接收器：SHOW 时构建高优先级通知（带提示音+震动），
 * 并在弹出后自动续排下一天（[HabitReminderScheduler.scheduleNext]），保证每天到点。
 * 通过 Hilt @EntryPoint 获取仓库与设置（尊重全局"音效"开关）。
 */
class HabitReminderReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "HabitReminderReceiver"
        const val ACTION_SHOW = "com.tickclear.app.habit.reminder.SHOW"
        const val EXTRA_HABIT_ID = "habit_id"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val appCtx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        // 接收诊断（W 级，常显）：习惯到点没收到通知时，logcat 过滤本标签即可确认广播是否投递。
        AppLogger.w(TAG, "onReceive action=$action habitId=$habitId")
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (action == ACTION_SHOW) showNotification(appCtx, habitId)
            } finally {
                pending.finish()
                scope.cancel() // 单次广播完成即回收局部作用域，不影响后续复用
            }
        }
    }

    private suspend fun showNotification(context: Context, habitId: String) {
        val ep = EntryPointAccessors.fromApplication(context, HabitReminderScheduler.HabitReminderEntryPoint::class.java)
        val habit = ep.habitRepository().getHabit(habitId)
        if (habit == null) {
            // 习惯已删除但闹钟残留：静默丢弃并撤闹钟，避免幽灵通知/漏通知。
            AppLogger.w(TAG, "showNotification 习惯不存在（可能已删），撤闹钟 habitId=$habitId")
            HabitReminderScheduler.cancelForHabit(context, habitId)
            return
        }
        // 触发后续提醒（续排下一天），与弹出解耦；续排失败不阻断当次通知。
        try {
            HabitReminderScheduler.scheduleNext(context, habitId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "scheduleNext 失败（不影响本次通知）：${e.message}")
        }

        // 习惯提醒：始终高优先级（带提示音+震动+抬头），不受全局「声音」开关约束——
        // 习惯提醒本质即"要被提醒"，与任务高优先级一致，避免开关关闭时静默丢失。
        val channel = NotificationHelper.CHANNEL_HIGH
        val priority = NotificationCompat.PRIORITY_MAX

        val title = if (habit.emoji.isNotEmpty()) "${habit.emoji} ${habit.title}" else habit.title
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notify_habit_text))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, habitId))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val chime = android.net.Uri.parse(
            "${android.content.ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.notify_chime}",
        )
        builder.setSound(chime)
        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
        // 习惯提醒用抬头通知（HIGH 渠道已含），不抢占全屏，避免每天强制弹窗打扰。
        val nm = notificationManager(context)
        val imp = nm.getNotificationChannel(channel)?.importance ?: -1
        val globalSoundEnabled = ep.settingsRepository().soundEnabled.first()
        AppLogger.w(TAG, "showNotification 弹出 habit=${habit.id} title=${habit.title} channel=$channel 全局声音=$globalSoundEnabled 系统重要性=$imp (>=4 应响铃震动)")
        nm.notify(ReminderIds.fnv1a("habit:$habitId"), builder.build())
    }

    private fun openAppIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, ReminderIds.fnv1a("habitc:$habitId"), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
