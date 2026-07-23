package com.tickclear.app.domain.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 系统框架本地语音识别封装（零依赖，[android.speech.SpeechRecognizer]）。
 *
 * ⚠️ 局限（务必知悉）：这是「系统识别服务兜底」方案，并非神经网络离线模型。
 * - 多数设备实际依赖厂商/Google 识别服务，可能需联网；
 * - 是否真正离线取决于系统是否提供 on-device 识别（部分设备支持 [RecognizerIntent.EXTRA_PREFER_OFFLINE]）；
 * - 真正离线神经网络唤醒词/ASR 需引入模型文件 + 推理运行时（如 Vosk / TFLite），与项目「不引入新依赖」约束冲突，故未采用。
 *
 * 用法：单句识别（本地 ASR 语音输入）传 [continuous]=false；持续监听（唤醒词）传 true，框架会在无匹配/超时时自动重启。
 */
class LocalSpeechRecognizer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var continuous = false
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null

    /** 系统是否提供语音识别能力。 */
    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        continuous: Boolean,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
    ) {
        if (running) return
        if (!isAvailable) {
            onFinal("")
            return
        }
        this.continuous = continuous
        this.onPartial = onPartial
        this.onFinal = onFinal
        running = true
        mainHandler.post { createAndStart() }
    }

    private fun createAndStart() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotEmpty()) onPartial?.invoke(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            onFinal?.invoke(text)
            if (continuous && running) startListeningInternal()
        }

        override fun onError(error: Int) {
            // 持续监听模式：无匹配 / 超时自动重启；其余错误停止避免空转。
            if (continuous && running &&
                (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                startListeningInternal()
            } else {
                onFinal?.invoke("")
                running = false
            }
        }
    }

    /** 停止并释放识别器。 */
    fun stop() {
        running = false
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }
}
