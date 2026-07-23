package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.data.repositories.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 火山引擎豆包（Doubao）大模型，OpenAI 兼容端点（Ark 推理端点）。
 * 密钥为 Ark API Key，存于加密存储 [SettingsRepository.PREF_LLM_API_KEY_DOUBAO]。
 */
@Singleton
class DoubaoLlmProvider @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
) : OpenAiCompatibleLlmProvider(
    context = context,
    settingsRepository = settingsRepository,
    providerId = LlmProviderCatalog.DOUBAO,
    defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
    defaultModel = "doubao-seed-1-6",
    apiKeyPrefKey = SettingsRepository.PREF_LLM_API_KEY_DOUBAO,
)
