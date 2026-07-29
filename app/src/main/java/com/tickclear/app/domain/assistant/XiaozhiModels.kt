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

    /** V2.17：连接意外断开后正在自动重连（第 attempt/max 次，指数退避）。 */
    data class Reconnecting(val attempt: Int, val max: Int) : XiaozhiEvent

    /**
     * V2.8X++：启动期连接诊断（握手超时 / 升级成功但握手前被关 / 服务端拒绝）。
     * 由 UI 层用顶栏 banner 展示，**不写入消息流**——用户进 tab 时不希望看到一堆连接错误。
     * 真正的对话期错误（用户已发消息后链路断）仍走 [Error] 进消息流。
     */
    data class ConnectionIssue(val detail: String) : XiaozhiEvent

    /** 连接建立失败（如端点非法 / 建连异常）：用于 UI 回显，不触发重连（P0）。 */
    data class Error(val detail: String) : XiaozhiEvent

    data object Disconnected : XiaozhiEvent
}
