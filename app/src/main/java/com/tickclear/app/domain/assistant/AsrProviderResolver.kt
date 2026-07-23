package com.tickclear.app.domain.assistant

import com.tickclear.app.data.repositories.SettingsRepository
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按当前设置解析文件式 [AsrProvider] 实现（多服务商路由，P5.5）。
 *
 * 通过 Hilt `@IntoMap` 多绑定注入各 Provider（key = [AsrProviderCatalog] id），按设置直接查表路由，
 * 不再需要 `when` 硬编码分支；新增服务商只需在 [AssistantProviderBindings] 加一个 @Binds。
 * 小智（xiaozhi）/ 系统识别（system）不在映射内 → 返回 null，走对应实时路径。
 */
@Singleton
class AsrProviderResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providers: Map<String, @JvmSuppressWildcards AsrProvider>,
) {
    suspend fun resolve(): AsrProvider? = providers[settingsRepository.asrProvider.first()]
}
