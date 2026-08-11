package com.tickclear.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 「首次启动权限引导」状态契约（V2.13.2）。
 *
 * 设计要点：
 * - **独立 DataStore**：与 [SettingsRepository] 完全解耦，避免「备份导入/导出」时
 *   把 v2.13.0 已完成引导的用户状态意外重置到未完成，引发跨版本体验漂移。
 * - **只持久化一个标志**：权限真实状态（[com.tickclear.app.domain.scheduler.PermissionChecker]
 *   现场扫描）由 ViewModel 直接拉取，不缓存，避免与系统真实状态漂移。
 *
 * 写入时机：
 * - 用户完成 4 步引导或主动跳过 → [setIntroDone](true)
 * - 用户在「设置 → 高级 → 权限配置」中点击「重新跑引导」→ [setIntroDone](false)
 */
interface PermissionIntroRepository {
    /** 引导是否已完成（true = 已完成/跳过；false = 待展示）。首次安装默认 false。 */
    val introDone: Flow<Boolean>

    /** 写入引导完成标志。 */
    suspend fun setIntroDone(done: Boolean)
}