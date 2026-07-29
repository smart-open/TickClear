package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 传输路由：根据 SettingsRepository.assistantMode 在 Mock（离线）与
 * 真实 WebSocket 之间切换。ViewModel 只持有此门面，无需感知当前模式。
 */
class XiaozhiTransportRouter(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val mcpTools: XiaozhiMcpTools,
    private val codec: OpusCodec,
) : XiaozhiTransport {

    private val _events = MutableSharedFlow<XiaozhiEvent>(extraBufferCapacity = 64)
    override val events: Flow<XiaozhiEvent> = _events.asSharedFlow()

    private val mock = MockXiaozhiTransport(context)
    private val real = WebSocketXiaozhiTransport(context, settings, mcpTools, codec)

    @Volatile private var active: XiaozhiTransport = mock
    private var forwardJob: Job? = null

    override suspend fun connect(prompt: String) {
        val mode = settings.assistantMode.first()
        active = if (mode == "REAL") real else mock
        forwardJob?.cancel()
        forwardJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            active.events.collect { _events.emit(it) }
        }
        active.connect(prompt)
    }

    override suspend fun sendText(text: String) {
        active.sendText(text)
    }

    override suspend fun sendListenStart() {
        active.sendListenStart()
    }

    override suspend fun sendListenStop() {
        active.sendListenStop()
    }

    override fun sendAudio(bytes: ByteArray) {
        active.sendAudio(bytes)
    }

    override suspend fun disconnect() {
        forwardJob?.cancel()
        forwardJob = null
        mock.disconnect()
        real.disconnect()
    }
}
