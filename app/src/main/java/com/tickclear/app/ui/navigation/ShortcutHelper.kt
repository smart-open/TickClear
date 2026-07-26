package com.tickclear.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.annotation.TargetApi
import android.annotation.SuppressLint
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.tickclear.app.MainActivity
import com.tickclear.app.R

/**
 * V2.9 动态快捷方式（长按启动图标展开）。
 * 采用系统 [ShortcutManager]（API 25+），零第三方依赖；minSdk 24 以下静默跳过。
 * 快捷方式经 [MainActivity] 携带 [EXTRA_SHORTCUT_ACTION] 冷/热启动，由导航图消费并跳转。
 */
object ShortcutHelper {
    const val EXTRA_SHORTCUT_ACTION = "com.tickclear.app.SHORTCUT_ACTION"
    const val ACTION_NEW_TASK = "new_task"
    const val ACTION_ASSISTANT = "assistant"
    const val ACTION_TODAY = "today"

    @SuppressLint("ReportShortcutUsage")
    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = listOf(
            build(
                context,
                ACTION_NEW_TASK,
                R.string.shortcut_new_task_short,
                R.string.shortcut_new_task_long,
                android.R.drawable.ic_input_add,
            ),
            build(
                context,
                ACTION_ASSISTANT,
                R.string.shortcut_assistant_short,
                R.string.shortcut_assistant_long,
                android.R.drawable.ic_btn_speak_now,
            ),
            build(
                context,
                ACTION_TODAY,
                R.string.shortcut_today_short,
                R.string.shortcut_today_long,
                android.R.drawable.ic_menu_agenda,
            ),
        )
        runCatching { manager.dynamicShortcuts = shortcuts }
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun build(
        context: Context,
        action: String,
        shortRes: Int,
        longRes: Int,
        iconRes: Int,
    ): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            putExtra(EXTRA_SHORTCUT_ACTION, action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return ShortcutInfo.Builder(context, "sc_$action")
            .setShortLabel(context.getString(shortRes))
            .setLongLabel(context.getString(longRes))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
