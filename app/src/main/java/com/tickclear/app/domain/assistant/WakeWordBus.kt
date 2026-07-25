package com.tickclear.app.domain.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 跨组件传递唤醒事件的轻量单例总线（V2.66）：前台 [WakeWordService] 检测到语音后调用 [wake]，
 * 唤起 MainActivity 跳转到助手页，并由 AssistantViewModel 消费 [pendingAutoVoice] / 监听 [events] 自动开始收音。
 */
object WakeWordBus {
    /** 待消费的自动语音标记：服务唤醒后置 true，助手页消费后清零。 */
    var pendingAutoVoice: Boolean = false
        private set

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun wake() {
        pendingAutoVoice = true
        _events.tryEmit(Unit)
    }

    fun consumePending(): Boolean {
        val v = pendingAutoVoice
        pendingAutoVoice = false
        return v
    }
}
