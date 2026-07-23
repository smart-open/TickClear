package com.tickclear.app.domain.assistant

import android.content.Context
import kotlinx.coroutines.flow.first
import com.tickclear.app.domain.repository.SettingsRepository

/**
 * 离线语音唤醒词（best-effort，系统识别服务兜底，零依赖）。
 *
 * 复用 [LocalSpeechRecognizer] 持续识别，检测设置中的唤醒词短语；命中即触发 [onWake] 并停止监听（单次触发）。
 * ⚠️ 与 [LocalSpeechRecognizer] 同样：依赖系统识别服务，并非神经网络离线模型；若要真正离线神经网络唤醒词需引入 ML 运行时（与「不引入新依赖」冲突）。
 */
class WakeWordManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val recognizer = LocalSpeechRecognizer(context)
    private var active = false

    val isAvailable: Boolean get() = recognizer.isAvailable

    /** 开始持续监听唤醒词；命中时回调 [onWake]（已在主线程）。 */
    suspend fun start(onWake: () -> Unit) {
        if (active || !recognizer.isAvailable) return
        active = true
        val phrase = settingsRepository.wakeWord.first().trim().replace(WS, "").lowercase()
        recognizer.start(
            continuous = true,
            onPartial = { /* 唤醒词检测以终句为准，减少误触发 */ },
            onFinal = { text ->
                if (!active) return@start
                val hit = text.trim().replace(WS, "").lowercase()
                    .let { it == phrase || (phrase.isNotEmpty() && it.contains(phrase)) }
                if (hit) {
                    stop()
                    onWake()
                }
            },
        )
    }

    /** 停止监听。 */
    fun stop() {
        active = false
        recognizer.stop()
    }

    val isActive: Boolean get() = active

    companion object {
        // 提升为常量避免每次 start/onFinal 重复编译（性能）。
        private val WS = Regex("\\s+")
    }
}
