package com.tickclear.app.domain.permission

/**
 * 引导页与「设置 → 高级 → 权限配置」复访页的统一权限快照（V2.13.2）。
 * 每次现场扫描（[com.tickclear.app.domain.scheduler.PermissionChecker]），
 * 不缓存——避免与系统真实状态漂移（用户可能中途去系统设置开关权限）。
 *
 * 字段命名遵循「小写驼峰 + 名词化」风格（与其它 model 保持一致）。
 */
data class PermissionStatus(
    /** 通知权限（POST_NOTIFICATIONS）。API33+ 才有意义，< 33 永远 true。 */
    val notification: Boolean,
    /** 精确闹钟（SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM）。< 31 永远 true。 */
    val exactAlarm: Boolean,
    /** 全屏通知意图（USE_FULL_SCREEN_INTENT）。< 34 永远 true。 */
    val fullScreenIntent: Boolean,
    /** 电池优化白名单（isIgnoringBatteryOptimizations）。< 23 永远 true。 */
    val batteryOptimization: Boolean,
    /** 悬浮窗（SYSTEM_ALERT_WINDOW）。< 23 永远 true。 */
    val drawOverlays: Boolean,
    /** 定位（ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION）。 */
    val location: Boolean,
    /** 麦克风（RECORD_AUDIO）。 */
    val microphone: Boolean,
    /** 相机（CAMERA）。 */
    val camera: Boolean,
) {
    /**
     * 是否所有「首次启动必须确认」的权限均已就绪（通知 + 定位）。
     * 其余（闹钟/电池/悬浮窗）用户可在系统设置内补，不强制阻塞引导完成。
     */
    val isCoreReady: Boolean get() = notification && location
}