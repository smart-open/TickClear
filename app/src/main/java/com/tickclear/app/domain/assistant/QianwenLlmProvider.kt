package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阿里通义千问（Qianwen / DashScope），OpenAI 兼容端点（compatible-mode）。
 * 密钥为 DashScope API Key，存于加密存储 [SettingsRepository.PREF_LLM_API_KEY_QIANWEN]。
 */
@Singleton
class QianwenLlmProvider @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
) : OpenAiCompatibleLlmProvider(
    context = context,
    settingsRepository = settingsRepository,
    providerId = LlmProviderCatalog.QIANWEN,
    defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultModel = "qwen-plus",
    apiKeyPrefKey = SettingsRepository.PREF_LLM_API_KEY_QIANWEN,
)
