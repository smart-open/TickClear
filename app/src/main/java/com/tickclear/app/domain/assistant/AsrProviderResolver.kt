package com.tickclear.app.domain.assistant

import com.tickclear.app.data.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按当前设置解析文件式 [AsrProvider] 实现（多服务商路由，P5.5）。
 *
 * - 小智（xiaozhi）由小智服务端完成识别，不实现文件式接口，返回 null；
 * - system（本地系统识别）走 [LocalSpeechRecognizer] 实时路径，不实现文件式接口，返回 null；
 * - OpenAI / 腾讯云 / 阿里云均走 [AsrProvider.transcribe] 上传文件，按 [SettingsRepository.asrProvider] 选择。
 */
@Singleton
class AsrProviderResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val openAi: WhisperCompatibleAsrProvider,
    private val tencent: TencentAsrProvider,
    private val aliyun: AliyunAsrProvider,
) {
    suspend fun resolve(): AsrProvider? = when (settingsRepository.asrProvider.first()) {
        AsrProviderCatalog.OPENAI -> openAi
        AsrProviderCatalog.TENCENT -> tencent
        AsrProviderCatalog.ALIYUN -> aliyun
        else -> null
    }
}
