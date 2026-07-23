package com.tickclear.app.domain.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tickclear.app.MainActivity
import com.tickclear.app.FullScreenAlertActivity
import com.tickclear.app.R
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.model.RepeatType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 提醒广播接收器：
 * - SHOW：构建并弹出任务提醒通知（按 reminderLevel 决定优先级）；
 * - COMPLETE：经 CompleteTaskUseCase 完成任务（通知栏直接标记完成）；
 * - SNOOZE：稍后提醒（默认 15 分钟，覆盖原闹钟）；
 * - SKIP：跳过本次实例（仅重复任务有意义），保留任务、不计入完成。
 *
 * 通知动作均通过 Hilt @EntryPoint 获取仓库与 UseCase 执行业务。
 */
class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_SHOW = "com.tickclear.app.reminder.SHOW"
        const val ACTION_COMPLETE = "com.tickclear.app.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.tickclear.app.reminder.SNOOZE"
        const val ACTION_SKIP = "com.tickclear.app.reminder.SKIP"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_INSTANCE_ID = "instance_id"
        const val EXTRA_DELAY_MIN = "delay_min"
        const val SNOOZE_DEFAULT_MIN = 15
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val appCtx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "$taskId@today"
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_SHOW -> showNotification(appCtx, taskId, instanceId)
                    ACTION_COMPLETE -> complete(appCtx, taskId, instanceId)
                    ACTION_SNOOZE -> {
                        val delay = intent.getIntExtra(EXTRA_DELAY_MIN, SNOOZE_DEFAULT_MIN)
                        ReminderScheduler.scheduleSnooze(appCtx, instanceId, taskId, delay)
                        cancelNotification(appCtx, taskId)
                    }
                    ACTION_SKIP -> skip(appCtx, taskId, instanceId)
                }
            } finally {
                pending.finish()
                scope.cancel() // 单次广播任务完成即回收作用域，避免泄漏（L1）
            }
        }
    }

    private suspend fun showNotification(context: Context, taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(context, ReminderScheduler.ReminderEntryPoint::class.java)
        val task = ep.taskRepository().getById(taskId) ?: return
        var level = task.reminderLevel

        // 静音时段：命中且为低优先级时降级为静默（不响铃震动、不占状态栏角标）。
        val settings = ep.settingsRepository()
        val quietEnabled = settings.quietHoursEnabled.first()
        if (quietEnabled && level == "low") {
            val cal = java.util.Calendar.getInstance()
            val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val start = settings.quietStartMin.first()
            val end = settings.quietEndMin.first()
            if (com.tickclear.app.domain.repository.SettingsRepository.isInQuietWindow(nowMin, start, end)) {
                level = "silent"
            }
        }

        val channel = if (level == "silent") NotificationHelper.CHANNEL_SILENT else NotificationHelper.channelForLevel(level)
        val priority = when (level) {
            "high" -> NotificationCompat.PRIORITY_MAX
            "low" -> NotificationCompat.PRIORITY_LOW
            "silent" -> NotificationCompat.PRIORITY_MIN
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.notify_reminder_text))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, instanceId.hashCode()))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_complete),
                actionIntent(context, ACTION_COMPLETE, taskId, instanceId, instanceId.hashCode()),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_snooze),
                actionIntent(context, ACTION_SNOOZE, taskId, instanceId, (instanceId + "z").hashCode(), SNOOZE_DEFAULT_MIN),
            )
        if (RepeatType.fromCode(task.repeatType) != RepeatType.NONE) {
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_skip),
                actionIntent(context, ACTION_SKIP, taskId, instanceId, (instanceId + "k").hashCode()),
            )
        }
        if (level == "high") {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            // 高优先级提醒：附加全屏意图。Android 14+ 仅在系统允许（已获「额外提醒权限」或应用前台）时真正全屏，
            // 否则系统静默降级为普通通知；此处仅在允许时附加，避免无效附加。
            if (Build.VERSION.SDK_INT < 34 || notificationManager(context).canUseFullScreenIntent()) {
                builder.setFullScreenIntent(fullScreenIntent(context, taskId, instanceId), true)
            }
        }
        // 以 instanceId 作为通知键：避免不同任务 hashCode 碰撞，且重复任务多实例各自独立（互不覆盖）。
        notificationManager(context).notify(instanceId.hashCode(), builder.build())
    }

    private suspend fun complete(context: Context, taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(context, ReminderScheduler.ReminderEntryPoint::class.java)
        val task = ep.taskRepository().getActiveById(taskId) ?: ep.taskRepository().getById(taskId) ?: return
        ep.completeTaskUseCase()(task, instanceId)
        cancelNotification(context, instanceId)
    }

    private suspend fun skip(context: Context, taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(context, ReminderScheduler.ReminderEntryPoint::class.java)
        val date = instanceId.substringAfter('@', "")
        if (date.isNotEmpty()) {
            ep.taskInstanceRepository().skip(instanceId, taskId, date)
        }
        cancelNotification(context, instanceId)
    }

    private fun openAppIntent(context: Context, req: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        taskId: String,
        instanceId: String,
        req: Int,
        delayMin: Int = 0,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
            if (delayMin > 0) putExtra(EXTRA_DELAY_MIN, delayMin)
        }
        return PendingIntent.getBroadcast(
            context, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun cancelNotification(context: Context, instanceId: String) {
        notificationManager(context).cancel(instanceId.hashCode())
    }

    /** 全屏提醒意图：跳转 [FullScreenAlertActivity] 展示醒目提醒。 */
    private fun fullScreenIntent(context: Context, taskId: String, instanceId: String): PendingIntent {
        val intent = Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        }
        return PendingIntent.getActivity(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 发送一条测试通知（诊断用，不经过具体任务，便于核对通知渠道与优先级）。 */
    fun fireTestNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.debug_test_notification))
            .setContentText(context.getString(R.string.notify_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, "test".hashCode()))
        notificationManager(context).notify("tickclear_test".hashCode(), builder.build())
    }
}
