package com.tickclear.app.domain.assistant

/** 小智设备协议事件（hello/listen/stt/llm/tts + mcp 工具调用）的领域抽象。 */
sealed interface XiaozhiEvent {
    /** 连接建立（hello 握手完成）。 */
    data object Connected : XiaozhiEvent

    /** ASR 识别出的文本（模拟模式下即用户输入文本）。 */
    data class SttText(val text: String) : XiaozhiEvent

    /** LLM 回复文本。 */
    data class LlmText(val text: String) : XiaozhiEvent

    /** TTS 播报文本（界面用不到，保留以对齐协议）。 */
    data class TtsText(val text: String) : XiaozhiEvent

    /** MCP JSON-RPC 工具调用（如 create_task）。 */
    data class McpToolCall(
        val tool: String,
        val arguments: Map<String, Any?>,
    ) : XiaozhiEvent

    /** 真实模式下由传输层完成 MCP 调用后的回执（仅用于 UI 展示，不再二次执行）。 */
    data class McpToolResult(
        val message: String,
        val taskCreated: Boolean,
    ) : XiaozhiEvent

    data object Disconnected : XiaozhiEvent
}
