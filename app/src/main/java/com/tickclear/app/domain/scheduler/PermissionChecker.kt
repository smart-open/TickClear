package com.tickclear.app.domain.scheduler

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 提醒相关系统权限检测（零依赖，纯框架 API）。
 * - 全屏提醒（Android 14+）：[canUseFullScreenIntent]
 * - 精确闹钟（Android 12+）：[canScheduleExactAlarms]
 * 低版本上对应能力恒可用，故均返回 true，调用方无需再做版本判断。
 *
 * V2.13.2 扩展：补充「首次启动权限引导」所需的运行时权限检测方法，统一在此处
 * 维护，避免散落在各 ViewModel。所有系统 API 均 [runCatching] 兜底，任何异常一律
 * 返回安全默认值（false 表示不可用，引导页提示用户去系统设置）。
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

    // ── 引导页所需的运行时权限检测（统一在此扩展，调用方只依赖 PermissionChecker） ──

    /**
     * Android 13+(TIRAMISU)是否授予 POST_NOTIFICATIONS。
     * 12 及以下无此运行时权限（安装即默认授予），故永远 true。
     */
    fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return runCatching {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * 是否已加入电池优化白名单（即系统不杀后台进程）。
     * Android 23+ 才有意义；< 23 永远 true。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * 是否可绘制系统级悬浮窗（SYSTEM_ALERT_WINDOW）。
     * Android 23+ 才有意义；< 23 永远 true。
     */
    fun canDrawOverlays(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }

    /** 定位（粗/细任一）是否已授予。 */
    fun isLocationGranted(context: Context): Boolean {
        val fine = runCatching {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }.getOrNull() == PackageManager.PERMISSION_GRANTED
        val coarse = runCatching {
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.getOrNull() == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** 麦克风是否已授予。 */
    fun isMicrophoneGranted(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 相机是否已授予。 */
    fun isCameraGranted(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}
