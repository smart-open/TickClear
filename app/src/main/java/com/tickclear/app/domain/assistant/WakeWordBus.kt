package com.tickclear.app.domain.assistant

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 跨组件传递唤醒事件的轻量单例总线（V2.66）：前台 [WakeWordService] 检测到语音后调用 [wake]，
 * 唤起 MainActivity 跳转到助手页，并由 AssistantViewModel 消费 [pendingAutoVoice] / 监听 [events] 自动开始收音。
 */
object WakeWordBus {
    /**
     * 待消费的自动语音标记：服务唤醒后置 true，助手页消费后清零。
     *
     * V2.8X 修复：原为普通 `var Boolean`。写入方是前台服务的音频线程、读取方是主线程，
     * 既无内存可见性保证，`consumePending()` 的「读-改-写」也不是原子的 ——
     * 极端情况下会漏消费（唤醒后不自动收音）或双消费（重复开麦）。改用 [AtomicBoolean]。
     */
    private val pending = AtomicBoolean(false)

    val pendingAutoVoice: Boolean get() = pending.get()

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun wake() {
        pending.set(true)
        _events.tryEmit(Unit)
    }

    /** 原子取走标记：并发调用只有一个能拿到 true。 */
    fun consumePending(): Boolean = pending.getAndSet(false)
}
