package com.tickclear.app.domain.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一日志基础设施（v2.0 / V2.2）。
 *
 * - 镜像写入系统 Logcat（保持原有可观测性，便于 adb logcat 抓包）。
 * - 额外维护内存环形缓冲（上限 [MAX_BUFFER]），供 Debug 页「运行日志」在屏查看与导出，
 *   解决原散落 `Log.w/e` 调用点无法在应用内回看的问题。
 * - 纯本地、无网络、无第三方 SDK，符合项目「纯本地 · 无云端」定位。
 */
enum class LogLevel(val priority: Int, val letter: String) {
    VERBOSE(Log.VERBOSE, "V"),
    DEBUG(Log.DEBUG, "D"),
    INFO(Log.INFO, "I"),
    WARN(Log.WARN, "W"),
    ERROR(Log.ERROR, "E"),
}

data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

object AppLogger {

    private const val MAX_BUFFER = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    private fun emit(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        val list = _entries.value.toMutableList()
        list.add(LogEntry(System.currentTimeMillis(), level, tag, message, throwable))
        while (list.size > MAX_BUFFER) list.removeAt(0)
        _entries.value = list
    }

    fun v(tag: String, msg: String) = emit(LogLevel.VERBOSE, tag, msg, null)
    fun d(tag: String, msg: String) = emit(LogLevel.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = emit(LogLevel.INFO, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = emit(LogLevel.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = emit(LogLevel.ERROR, tag, msg, t)

    /** 最近 [n] 条（默认全部缓冲）。 */
    fun recent(n: Int = MAX_BUFFER): List<LogEntry> = _entries.value.takeLast(n)

    fun clear() {
        _entries.value = emptyList()
    }

    /** 序列化为纯文本（供导出 / 在屏展示）。 */
    fun formatPlain(lines: List<LogEntry> = _entries.value): String {
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        return lines.joinToString("\n") { e ->
            val ts = fmt.format(Date(e.timeMillis))
            val throwableText = e.throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
            "$ts ${e.level.letter}/${e.tag}: ${e.message}$throwableText"
        }
    }
}
