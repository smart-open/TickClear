package com.tickclear.app.domain.assistant

import kotlinx.coroutines.flow.Flow

/**
 * 小智传输层抽象。
 * - Mock 实现：进程内模拟，离线可用（见 MockXiaozhiTransport）。
 * - 真实实现：WebSocketXiaozhiTransport(OkHttp) + Opus 编解码 + 麦克风采集/播放。
 *   编码不可用（多数设备无 Opus 编码器）时由 UI 优雅降级为文字输入。
 */
interface XiaozhiTransport {
    val events: Flow<XiaozhiEvent>

    /** 建立连接并完成 hello 握手（携带自定义 prompt 人设）。 */
    suspend fun connect(prompt: String)

    /** 发送一段语音识别结果（模拟模式下即用户输入的文本）。 */
    suspend fun sendText(text: String)

    /** 开启语音采集（真实模式：发 listen start，进入聆听态）。 */
    suspend fun sendListenStart()

    /** 结束语音采集（真实模式：发 listen stop）。 */
    suspend fun sendListenStop()

    /**
     * 立即终止本地 TTS 外放（停止 AudioTrack）。用于"录音即打断对方"：用户开始说话的瞬间，
     * 设备扬声器可能仍在播放小智上一轮回复的尾音，若不清掉会被麦克风录入形成回声
     * （表现为"发送出去的信息是小智自己说的话"）。与 [sendListenStart] 配合：前者停服务端生成，
     * 本方法停设备侧播放，双管齐下确保录音期间麦克风只能听到用户。
     */
    fun abortTts()

    /** 恢复本地 TTS 外放（停止录音/打断结束后调用，使小智的回答恢复出声）。 */
    fun resumeTts()

    /** 发送一帧编码后的 Opus 音频（真实模式下经 WebSocket 二进制帧）。 */
    fun sendAudio(bytes: ByteArray)

    suspend fun disconnect()
}
