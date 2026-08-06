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
import com.tickclear.app.domain.log.AppLogger
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
    // 注意：不可把 CoroutineScope 存为实例字段并在 finally 中 cancel —— 系统会复用同一
    // receiver 实例投递多条提醒，首条完成即 cancel 共享 scope，后续 onReceive 在已取消的
    // scope 上 launch 永不执行，导致提醒被静默丢弃且 goAsync() 的 PendingResult 泄露。
    // 因此每次 onReceive 使用独立的局部 scope，随本次广播生命周期结束。

    companion object {
        const val TAG = "ReminderReceiver"
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
        // 接收诊断（W 级，常显）：告警"到点没收到通知"时，logcat 过滤本标签即可确认广播是否真的被投递。
        AppLogger.w(TAG, "onReceive action=$action taskId=$taskId instanceId=$instanceId")
        val pending = goAsync()
        val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        localScope.launch {
            try {
                when (action) {
                    ACTION_SHOW -> showNotification(appCtx, taskId, instanceId)
                    ACTION_COMPLETE -> complete(appCtx, taskId, instanceId)
                    ACTION_SNOOZE -> {
                        val delay = intent.getIntExtra(EXTRA_DELAY_MIN, SNOOZE_DEFAULT_MIN)
                        // 沿用任务自身的提醒级别，避免高优先级提醒在「稍后提醒」后被降级（见 scheduleSnooze 注释）。
                        val level = runCatching {
                            EntryPointAccessors
                                .fromApplication(appCtx, ReminderScheduler.ReminderEntryPoint::class.java)
                                .taskRepository()
                        }.getOrNull()?.getById(taskId)?.reminderLevel ?: "mid"
                        ReminderScheduler.scheduleSnooze(appCtx, instanceId, taskId, delay, level)
                        // 修复：此前误传 taskId —— 通知 ID 由 instanceId 哈希得出，
                        // 传 taskId 算出的是另一个 ID，点「稍后提醒」后原通知根本不会消失。
                        cancelNotification(appCtx, instanceId)
                    }
                    ACTION_SKIP -> skip(appCtx, taskId, instanceId)
                }
            } finally {
                pending.finish()
                localScope.cancel() // 仅回收本次广播的局部作用域，不影响后续复用
            }
        }
    }

    private suspend fun showNotification(context: Context, taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(context, ReminderScheduler.ReminderEntryPoint::class.java)
        val task = ep.taskRepository().getById(taskId)
        if (task == null) {
            // 任务已被删除但闹钟残留：此处静默丢弃，记录以便排查"幽灵通知/漏通知"。
            AppLogger.w(TAG, "showNotification 任务不存在（可能已删），跳过通知 taskId=$taskId instanceId=$instanceId")
            return
        }
        // V2.8X 修复：此前仅判空。任务被软删（回收站）或用户已关掉该任务的提醒后，
        // 历史闹钟仍会照常弹出"幽灵通知"。这两种状态不会自愈（重新开启提醒必走编辑路径重排），
        // 因此就地撤销后续闹钟并返回。
        if (task.deletedAt != null || !task.reminderEnabled) {
            AppLogger.w(
                TAG,
                "showNotification 任务已删除或已关闭提醒，撤销残留闹钟 taskId=$taskId deleted=${task.deletedAt != null} reminderEnabled=${task.reminderEnabled}",
            )
            try {
                ReminderScheduler.cancelForTask(context, taskId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "撤销残留闹钟失败：${e.message}")
            }
            return
        }
        // 暂停 / 已完成（一次性）任务：本次不打扰，但保留闹钟链，恢复后无需重新编辑即可继续提醒。
        if (task.status != com.tickclear.app.domain.model.TaskStatus.ACTIVE.code) {
            AppLogger.w(TAG, "showNotification 任务非活动态(status=${task.status})，跳过本次提醒 taskId=$taskId")
            try {
                ReminderScheduler.scheduleNext(context, task)
            } catch (e: Exception) {
                AppLogger.e(TAG, "scheduleNext 失败：${e.message}")
            }
            return
        }
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

        val priority = when (level) {
            "high" -> NotificationCompat.PRIORITY_MAX
            "low" -> NotificationCompat.PRIORITY_LOW
            "silent" -> NotificationCompat.PRIORITY_MIN
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        // V2.30 稍后提醒时长取用户设置（默认 15 分钟）。
        val snoozeMin = settings.snoozeDefaultMin.first()
        // 全局「声音」开关。高优先级提醒是红线，用户显式选「高」= 务必响铃，故不受此开关约束。
        val globalSoundEnabled = settings.soundEnabled.first()
        val forcedSound = ReminderPrefs.shouldForceSound(level)

        // V2.8X 修复：该开关此前只写日志、从不生效（死设置）。Android 8+ 声音由渠道决定、
        // 无法在单条通知上关闭，因此关声音时把「中」提醒改投无声但保留震动的镜像渠道；
        // 「低/静默」本就无声无需处理，「高」按红线始终响铃。
        val muteMid = !globalSoundEnabled && level != "high"
        val channel = when {
            level == "silent" -> NotificationHelper.CHANNEL_SILENT
            muteMid && level != "low" -> NotificationHelper.CHANNEL_MID_MUTED
            else -> NotificationHelper.channelForLevel(level)
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.notify_reminder_text))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, ReminderIds.contentRequestCode(instanceId)))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_complete),
                actionIntent(context, ACTION_COMPLETE, taskId, instanceId, ReminderIds.completeRequestCode(instanceId)),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_snooze),
                actionIntent(context, ACTION_SNOOZE, taskId, instanceId, ReminderIds.snoozeRequestCode(instanceId), snoozeMin),
            )
        if (RepeatType.fromCode(task.repeatType) != RepeatType.NONE) {
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notify_action_skip),
                actionIntent(context, ACTION_SKIP, taskId, instanceId, ReminderIds.skipRequestCode(instanceId)),
            )
        }
        if (level == "high") {
            // V2.31→修正：高优先级 = 用户显式选择「高」，即表达「务必响铃+震动」意图，
            // 不再受全局「声音」开关约束。否则与调试页「测试通知」（绕开开关）行为不一致，
            // 且违背用户对高优先级提醒的预期。提示音用内置开源 CC0（Android 8.0- 由 builder 指定；8.0+ 由渠道控制）。
            val chime = android.net.Uri.parse(
                "${android.content.ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.notify_chime}",
            )
            builder.setSound(chime)
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 高优先级提醒：附加全屏意图。Android 14+ 仅在系统允许（已获「额外提醒权限」或应用前台）时真正全屏，
            // 否则系统静默降级为普通通知；此处仅在允许时附加，避免无效附加。
            if (Build.VERSION.SDK_INT < 34 || notificationManager(context).canUseFullScreenIntent()) {
                builder.setFullScreenIntent(fullScreenIntent(context, taskId, instanceId), true)
            }
        }
        // 以 instanceId 的稳定 FNV-1a 哈希作为通知键：避免不同任务哈希碰撞互相覆盖，
        // 且重复任务多实例各自独立（互不覆盖）。
        // 诊断（与测试通知一致）：打印渠道在系统层的真实重要性，便于区分「渠道被静音/勿扰」与「开关问题」。
        val nm = notificationManager(context)
        val imp = nm.getNotificationChannel(channel)?.importance ?: -1
        AppLogger.w(TAG, "showNotification 弹出 task=${task.id} title=${task.title} level=$level channel=$channel 强制响铃=$forcedSound 全局声音=$globalSoundEnabled 静音改投=$muteMid 系统重要性=$imp (>=4 应响铃震动)")
        nm.notify(ReminderIds.notificationId(instanceId), builder.build())
        // 续排下一发生日：保证重复任务持续提醒（一次性任务无后续发生日，自动跳过）。
        // 与弹出解耦，失败不影响本次通知展示。
        try {
            ReminderScheduler.scheduleNext(context, task)
        } catch (e: Exception) {
            AppLogger.e(TAG, "scheduleNext 失败（不影响本次通知）：${e.message}")
        }
    }

    private suspend fun complete(context: Context, taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(context, ReminderScheduler.ReminderEntryPoint::class.java)
        // 仅对未软删任务生效：已软删任务其通知应已被取消，此处不再兜底 getById，避免误完成已删除任务。
        val task = ep.taskRepository().getActiveById(taskId) ?: return
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
        notificationManager(context).cancel(ReminderIds.notificationId(instanceId))
    }

    /** 全屏提醒意图：跳转 [FullScreenAlertActivity] 展示醒目提醒。 */
    private fun fullScreenIntent(context: Context, taskId: String, instanceId: String): PendingIntent {
        val intent = Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        }
        return PendingIntent.getActivity(
            context, ReminderIds.fullScreenRequestCode(taskId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 发送一条测试通知（诊断用）：复刻真实高优先级提醒路径（高渠道 + 铃声 + 震动），便于确认通知栈工作。 */
    fun fireTestNotification(context: Context) {
        // 诊断：先把渠道在系统层的真实重要性打出来——若 <4(IMPORTANCE_HIGH) 说明设备端该渠道被降权/静音，
        // 此时即便代码正确也无声音/震动（Android 8+ 渠道不可就地升级，需靠新版本后缀的渠道 ID 重建）。
        val nm = notificationManager(context)
        val imp = nm.getNotificationChannel(NotificationHelper.CHANNEL_HIGH)?.importance ?: -1
        AppLogger.w(TAG, "fireTestNotification 发出 渠道=${NotificationHelper.CHANNEL_HIGH} 系统重要性=$imp (>=4 为高/应响铃震动；<4 多为设备被静音或勿扰)")
        val chime = android.net.Uri.parse(
            "${android.content.ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.notify_chime}",
        )
        val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.debug_test_notification))
            .setContentText(context.getString(R.string.notify_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(chime)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, ReminderIds.fnv1a("test_content")))
        nm.notify(ReminderIds.fnv1a("tickclear_test"), builder.build())
        AppLogger.w(TAG, "fireTestNotification 已 notify；若仍无声/无震：检查系统通知设置中该渠道声音/震动是否开启，或是否处于勿扰模式")
    }
}
