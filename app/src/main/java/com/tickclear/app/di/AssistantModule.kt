package com.tickclear.app.di

import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.domain.assistant.OpusCodec
import com.tickclear.app.domain.assistant.XiaozhiMcpTools
import com.tickclear.app.domain.assistant.XiaozhiTransport
import com.tickclear.app.domain.assistant.XiaozhiTransportRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {
    /** best-effort Opus 编解码器（MediaCodec 封装，单例复用避免重复创建）。 */
    @Provides
    @Singleton
    fun provideOpusCodec(): OpusCodec = OpusCodec()

    /**
     * 提供小智传输门面：根据 SettingsRepository.assistantMode 在
     * Mock（离线）与真实 WebSocket 之间切换；UI / ViewModel 不变。
     */
    @Provides
    @Singleton
    fun provideXiaozhiTransport(
        settings: SettingsRepository,
        mcpTools: XiaozhiMcpTools,
        codec: OpusCodec,
    ): XiaozhiTransport = XiaozhiTransportRouter(settings, mcpTools, codec)
}
