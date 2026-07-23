package com.tickclear.app.di

import com.tickclear.app.domain.assistant.AliyunAsrProvider
import com.tickclear.app.domain.assistant.AsrProvider
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.DoubaoLlmProvider
import com.tickclear.app.domain.assistant.LlmProvider
import com.tickclear.app.domain.assistant.LlmProviderCatalog
import com.tickclear.app.domain.assistant.OpenAiLlmProvider
import com.tickclear.app.domain.assistant.QianwenLlmProvider
import com.tickclear.app.domain.assistant.TencentAsrProvider
import com.tickclear.app.domain.assistant.WhisperCompatibleAsrProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * 多服务商 Provider 的 Hilt 多绑定（@IntoMap）。
 *
 * 各 [LlmProvider]/[AsrProvider] 实现以其 [LlmProviderCatalog]/[AsrProviderCatalog] id 为 key 注入 Map，
 * 使 [LlmProviderResolver]/[AsrProviderResolver] 无需 `when` 硬编码分支即可按设置路由。
 * 新增服务商 = 仅在此加一个 @Binds（无需改动 Resolver），扩展性更好。
 *
 * 小智（xiaozhi）/ 系统识别（system）不在此映射内，由 Resolver 返回 null 走对应实时路径。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantProviderBindings {
    @Binds @Singleton @IntoMap
    @StringKey(LlmProviderCatalog.OPENAI)
    abstract fun bindOpenAiLlm(p: OpenAiLlmProvider): LlmProvider

    @Binds @Singleton @IntoMap
    @StringKey(LlmProviderCatalog.DOUBAO)
    abstract fun bindDoubaoLlm(p: DoubaoLlmProvider): LlmProvider

    @Binds @Singleton @IntoMap
    @StringKey(LlmProviderCatalog.QIANWEN)
    abstract fun bindQianwenLlm(p: QianwenLlmProvider): LlmProvider

    @Binds @Singleton @IntoMap
    @StringKey(AsrProviderCatalog.OPENAI)
    abstract fun bindWhisperAsr(p: WhisperCompatibleAsrProvider): AsrProvider

    @Binds @Singleton @IntoMap
    @StringKey(AsrProviderCatalog.TENCENT)
    abstract fun bindTencentAsr(p: TencentAsrProvider): AsrProvider

    @Binds @Singleton @IntoMap
    @StringKey(AsrProviderCatalog.ALIYUN)
    abstract fun bindAliyunAsr(p: AliyunAsrProvider): AsrProvider
}
