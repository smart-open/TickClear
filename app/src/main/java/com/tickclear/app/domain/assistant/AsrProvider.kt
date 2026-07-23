package com.tickclear.app.domain.assistant

import java.io.File

/**
 * 语音识别（ASR）Provider 抽象。
 * 不同后端实现同一接口，由设置中的 [asrProvider] 选择：
 * - [XIAOZHI]：由小智服务端完成识别（默认，经 WebSocket 传输层，不实现本接口）；
 * - [OPENAI]：OpenAI 兼容的 /audio/transcriptions 接口（见 [WhisperCompatibleAsrProvider]）。
 *
 * 入参为一段已封装好容器格式的音频文件（当前统一用 WAV），返回识别文本。
 * 失败抛出 [com.tickclear.app.domain.model.AppException]。
 */
interface AsrProvider {
    /** 常量 id，与 SettingsRepository.asrProvider 取值一致。 */
    companion object {
        const val XIAOZHI = "xiaozhi"
        const val OPENAI = "openai"
    }

    /** 是否支持语音输入（用于 UI 判断是否展示麦克风）。 */
    val id: String

    /** 将音频文件转写为文本。实现应自行处理鉴权、网络与错误映射。 */
    suspend fun transcribe(audio: File): String

    /**
     * 凭据连通性自检：返回 true 表示必要凭据已配置且端点可达（不保证真实转写一定成功）。
     * 用于「测试并保存」前给出即时反馈，避免保存明显残缺的凭据配置。
     */
    suspend fun test(): Boolean
}
