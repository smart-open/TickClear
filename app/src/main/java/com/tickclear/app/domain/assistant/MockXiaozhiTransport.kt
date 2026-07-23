package com.tickclear.app.domain.assistant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay

/**
 * 离线模拟小智传输：进程内自包含，无需网络/SDK。
 * 对齐设备协议：hello 握手 → 拦截官方 welcome → 本地自定义欢迎；
 * 收到文本后识别是否为建任务意图，是则发出 mcp create_task 工具调用。
 */
class MockXiaozhiTransport : XiaozhiTransport {

    private val _events = MutableSharedFlow<XiaozhiEvent>(extraBufferCapacity = 64)
    override val events: Flow<XiaozhiEvent> = _events

    @Volatile private var connected = false

    override suspend fun connect(prompt: String) {
        if (connected) return
        connected = true
        _events.emit(XiaozhiEvent.Connected)
        // 拦截官方默认 welcome，改用本地自定义欢迎词（见落地文档 4.1/4.2）
        val welcome = "我在，请问有什么可以帮您？"
        _events.emit(XiaozhiEvent.LlmText(welcome))
        _events.emit(XiaozhiEvent.TtsText(welcome))
    }

    override suspend fun sendText(text: String) {
        if (!connected) return
        // 模拟 ASR 结果回显
        _events.emit(XiaozhiEvent.SttText(text))
        delay(120) // 模拟网络/推理延迟

        val parsed = TaskIntentParser.parse(text)
        if (parsed != null) {
            _events.emit(
                XiaozhiEvent.McpToolCall(
                    tool = "create_task",
                    arguments = mapOf(
                        "title" to parsed.title,
                        "date" to parsed.dateStr,
                        "minute" to parsed.minute,
                        "repeatType" to parsed.repeatType,
                        "weekdays" to parsed.weekdays,
                    ),
                ),
            )
            val whenText = when (parsed.repeatType) {
                "DAILY" -> "每天"
                "WEEKLY" -> "每周${parsed.weekdays}"
                else -> parsed.dateStr ?: "今天"
            }
            _events.emit(XiaozhiEvent.LlmText("好的，已为你记下「${parsed.title}」（$whenText）。"))
        } else {
            _events.emit(XiaozhiEvent.LlmText(genericReply(text)))
        }
    }

    override suspend fun sendListenStart() = Unit
    override suspend fun sendListenStop() = Unit
    override fun sendAudio(bytes: ByteArray) = Unit

    override suspend fun disconnect() {
        if (!connected) return
        connected = false
        _events.emit(XiaozhiEvent.Disconnected)
    }

    private fun genericReply(text: String): String = when {
        text.contains("你好") || text.contains("hi", ignoreCase = true) -> "你好呀，今天也要把小事一件件清空～"
        text.contains("你是谁") || text.contains("你叫什么") -> "我是你的专属小智助手，帮你记任务、陪你聊天。"
        text.contains("谢谢") -> "不客气，随时叫我～"
        text.length <= 6 -> "嗯嗯，我在听。需要我帮你记点什么吗？"
        else -> "收到～如果想记一件事，可以说「提醒我明天9点开会」这样哦。"
    }
}
