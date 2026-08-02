package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay

/**
 * 离线模拟小智传输：进程内自包含，无需网络/SDK。
 * 对齐设备协议：hello 握手 → 拦截官方 welcome → 本地自定义欢迎；
 * 收到文本后识别是否为建任务意图，是则发出 mcp create_task 工具调用。
 */
class MockXiaozhiTransport(
    private val context: Context,
) : XiaozhiTransport {

    private val _events = MutableSharedFlow<XiaozhiEvent>(extraBufferCapacity = 64)
    override val events: Flow<XiaozhiEvent> = _events

    @Volatile private var connected = false

    override suspend fun connect(prompt: String) {
        if (connected) return
        connected = true
        _events.emit(XiaozhiEvent.Connected)
        // V2.8X++：去掉默认欢迎语（"我是小九..."等）—— 用户希望「首次进入助手 tab 默认空白」，
        // 不再主动 seed 一条助手消息。等用户首条输入后再走 sendText 文本通道返回回复。
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
                        "title" to parsed.title.ifEmpty { context.getString(R.string.task_default_title) },
                        "date" to parsed.dateStr,
                        "minute" to parsed.minute,
                        "repeatType" to parsed.repeatType,
                        "weekdays" to parsed.weekdays,
                        "reminderOffset" to parsed.reminderOffsetMin,
                        "tags" to parsed.tags,
                        "notes" to parsed.notes,
                        "level" to parsed.level,
                    ),
                ),
            )
            val whenText = when (parsed.repeatType) {
                "DAILY" -> context.getString(R.string.repeat_daily)
                "WEEKLY" -> context.getString(R.string.repeat_weekly) + parsed.weekdays
                else -> parsed.dateStr ?: context.getString(R.string.assistant_mock_when_today)
            }
            _events.emit(XiaozhiEvent.LlmText(context.getString(R.string.assistant_mock_task_saved, parsed.title, whenText)))
        } else {
            _events.emit(XiaozhiEvent.LlmText(genericReply(text)))
        }
    }

    override suspend fun sendListenStart() = Unit
    override suspend fun sendListenStop() = Unit
    override fun abortTts() = Unit
    override fun resumeTts() = Unit
    override fun sendAudio(bytes: ByteArray) = Unit

    override suspend fun disconnect() {
        if (!connected) return
        connected = false
        _events.emit(XiaozhiEvent.Disconnected)
    }

    private fun genericReply(text: String): String = when {
        text.contains("你好") || text.contains("hi", ignoreCase = true) -> context.getString(R.string.assistant_mock_reply_hello)
        text.contains("你是谁") || text.contains("你叫什么") -> context.getString(R.string.assistant_mock_reply_who)
        text.contains("谢谢") -> context.getString(R.string.assistant_mock_reply_thanks)
        text.length <= 6 -> context.getString(R.string.assistant_mock_reply_short)
        else -> context.getString(R.string.assistant_mock_reply_default)
    }
}
