package com.tickclear.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.di.AppEntryPoint
import com.tickclear.app.domain.log.AppLogger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 「今日待办」桌面小组件（V2.8）：框架 [AppWidgetProvider] + [android.widget.RemoteViews]，
 * **零新依赖**（刻意不引入 androidx.glance，避免破坏「零新依赖」红线）。
 *
 * - 列表用 [TodayWidgetService]（RemoteViewsService）提供当日任务，点击行内勾选即完成。
 * - 标题栏点击打开应用。完成动作经显式广播回到本 Provider 的 [onReceive] 处理（goAsync 保活）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            // 集合数据：指向 RemoteViewsService。
            val serviceIntent = Intent(context, TodayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // 每个 widget 唯一，避免适配器复用冲突。
                data = android.net.Uri.parse("content://tickclear.widget/$id")
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 列表项点击模板（完成动作）：显式广播回本 Provider。
            val completeIntent = Intent(context, TodayWidgetProvider::class.java).apply { action = ACTION_COMPLETE }
            val completePi = PendingIntent.getBroadcast(
                context, REQ_COMPLETE, completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.widget_list, completePi)

            // 标题栏点击：打开应用。
            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPi)

            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_COMPLETE) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
                val task = entry.taskRepository().getById(taskId)
                if (task != null) entry.completeTaskUseCase()(task, instanceId, "widget")
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            } catch (e: Exception) {
                AppLogger.e("Widget", "完成失败", e)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.tickclear.app.WIDGET_COMPLETE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_INSTANCE_ID = "instance_id"
        private const val REQ_COMPLETE = 0x9111
        private const val REQ_OPEN = 0x9112
    }
}
