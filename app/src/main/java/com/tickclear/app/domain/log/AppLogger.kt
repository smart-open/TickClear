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

    /** 内存环形缓冲（上限 [MAX_BUFFER]），供 Debug 页在屏查看与导出。 */
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /**
     * V2.8X：调试日志总开关（设置 → 调试日志）。默认 **关闭**。
     *
     * - 关闭时：VERBOSE / DEBUG / INFO 三级直接丢弃（既不写 Logcat 也不入内存缓冲），
     *   避免常态运行下的字符串拼接与列表拷贝开销。
     * - **WARN / ERROR 始终记录**：这两级是崩溃与异常定位的最后一道线索，
     *   若一并关闭，用户报障时将无任何可回溯信息。
     *
     * 由 [com.tickclear.app.TickClearApplication] 在启动时按 DataStore 值同步，
     * 并在设置项切换时实时更新。@Volatile 保证跨线程可见性。
     */
    @Volatile
    var debugEnabled: Boolean = false
        private set

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    /** 该级别在当前开关状态下是否需要落盘/落缓冲。 */
    private fun shouldLog(level: LogLevel): Boolean =
        debugEnabled || level == LogLevel.WARN || level == LogLevel.ERROR

    @Synchronized
    private fun emit(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(level)) return
        // 镜像到系统 Logcat：在纯 JVM 单元测试环境（无 Android runtime）中，android.util.Log.*
        // 是抛 RuntimeException("Stub!") 的桩。此处兜底吞掉异常——该环境本无 Logcat 可镜像，
        // 仅保留内存环形缓冲即可；真实设备上这段正常执行，不影响可观测性。
        try {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, message, throwable)
                LogLevel.DEBUG -> Log.d(tag, message, throwable)
                LogLevel.INFO -> Log.i(tag, message, throwable)
                LogLevel.WARN -> Log.w(tag, message, throwable)
                LogLevel.ERROR -> Log.e(tag, message, throwable)
            }
        } catch (_: Throwable) {
            // 非 Android 环境（单元测试）：忽略 Logcat 镜像，仅保留内存缓冲。
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
            // Log.getStackTraceString 同样是 Android 桩，单元测试环境会抛 Stub!，兜底为空串。
            val throwableText = e.throwable?.let { t ->
                "\n" + runCatching { Log.getStackTraceString(t) }.getOrDefault("")
            } ?: ""
            "$ts ${e.level.letter}/${e.tag}: ${e.message}$throwableText"
        }
    }
}
