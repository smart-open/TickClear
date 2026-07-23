package com.tickclear.app.domain.assistant

/**
 * 可插拔的 LLM 服务商抽象（多服务商支持，P5.4/P5.5）。
 *
 * - 文本进、文本出；语音/Function Calling 等非文本能力由各实现自行决定是否支持；
 * - 小智（XiaozhiTransport）作为默认实现，本身支持语音+工具调用；
 * - OpenAI 兼容实现仅做文本对话，语音与建任务工具调用不在其范围内。
 * 失败统一抛 [com.tickclear.app.domain.model.AppException]，由调用方映射为用户提示。
 */
interface LlmProvider {
    /** 服务商唯一 id（存于设置，用于选择）。 */
    val id: String

    /** 展示名（配置屏下拉）。 */
    val label: String

    /** 一次文本对话：返回助手回复文本。 */
    suspend fun chat(systemPrompt: String, userText: String): String
}
