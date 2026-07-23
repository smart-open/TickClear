package com.tickclear.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.tickclear.app.R
import com.tickclear.app.di.AppEntryPoint
import com.tickclear.app.domain.conflict.instanceDueMinute
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 小组件集合数据源（V2.8）：经 [AppEntryPoint] 取 [GetTodayTasksUseCase] 获取当日任务，
 * 为每个任务行构建 [RemoteViews]；点击行内勾选携带 task/instance id 的 fill-in intent，
 * 由 [TodayWidgetProvider] 处理完成动作。
 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayWidgetFactory(applicationContext, intent)
}

class TodayWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID,
    )
    private var items: List<com.tickclear.app.domain.usecase.TodayItem> = emptyList()

    override fun onCreate() {}

    /** 后台线程调用：查询当日任务并缓存。 */
    override fun onDataSetChanged() {
        runCatching {
            val entry = EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
            items = runBlocking {
                withContext(Dispatchers.IO) { entry.getTodayTasksUseCase()().first() }.items
            }
        }.onFailure { items = emptyList() }
    }

    override fun onDestroy() { items = emptyList() }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_today_item)
        val rv = RemoteViews(context.packageName, R.layout.widget_today_item)
        rv.setTextViewText(R.id.widget_item_title, item.task.title)
        rv.setBoolean(R.id.widget_item_check, "setChecked", item.done)
        val min = item.task.instanceDueMinute()
        rv.setTextViewText(
            R.id.widget_item_time,
            if (min != null) "%02d:%02d".format(min / 60, min % 60) else "",
        )
        val fill = Intent().apply {
            putExtra(TodayWidgetProvider.EXTRA_TASK_ID, item.task.id)
            putExtra(TodayWidgetProvider.EXTRA_INSTANCE_ID, item.instanceId)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
