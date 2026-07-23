package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** OpenAI 官方兼容端点（Chat Completions）。 */
@Singleton
class OpenAiLlmProvider @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
) : OpenAiCompatibleLlmProvider(
    context = context,
    settingsRepository = settingsRepository,
    providerId = LlmProviderCatalog.OPENAI,
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModel = "gpt-4o-mini",
    apiKeyPrefKey = SettingsRepository.PREF_LLM_API_KEY_OPENAI,
)
