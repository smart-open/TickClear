package com.tickclear.app.domain.scheduler

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 提醒相关系统权限检测（零依赖，纯框架 API）。
 * - 全屏提醒（Android 14+）：[canUseFullScreenIntent]
 * - 精确闹钟（Android 12+）：[canScheduleExactAlarms]
 * 低版本上对应能力恒可用，故均返回 true，调用方无需再做版本判断。
 */
object PermissionChecker {
    /** Android 14+ 高优先级提醒是否可全屏弹出；低于 14 恒为 true。 */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(false)
    }

    /** Android 12+ 是否可调度精确闹钟；低于 12 恒为 true。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }
}
