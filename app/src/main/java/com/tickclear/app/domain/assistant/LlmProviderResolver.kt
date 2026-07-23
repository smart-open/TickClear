package com.tickclear.app.domain.assistant

import com.tickclear.app.data.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按当前设置解析 [LlmProvider] 实现（多服务商路由，P5.4）。
 *
 * - 小智（xiaozhi）由 [XiaozhiTransport] 承担，不在 [LlmProvider] 体系内，返回 null；
 * - OpenAI / 豆包 / 通义千问均为 OpenAI 兼容实现，按 [SettingsRepository.llmProvider] 选择。
 */
@Singleton
class LlmProviderResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val openAi: OpenAiLlmProvider,
    private val doubao: DoubaoLlmProvider,
    private val qianwen: QianwenLlmProvider,
) {
    suspend fun resolve(): LlmProvider? = when (settingsRepository.llmProvider.first()) {
        LlmProviderCatalog.OPENAI -> openAi
        LlmProviderCatalog.DOUBAO -> doubao
        LlmProviderCatalog.QIANWEN -> qianwen
        else -> null
    }
}
