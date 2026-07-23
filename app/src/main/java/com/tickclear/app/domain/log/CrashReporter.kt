package com.tickclear.app.domain.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃遥测（V2.3）：纯本地、无网络、无第三方 SDK，符合「零依赖」红线。
 *
 * - 安装全局未捕获异常处理器：崩溃时写持久文件 `filesDir/crash_log.txt`（追加、限长），
 *   并注入 [AppLogger] 环形缓冲 + Logcat，使 Debug 页「运行日志」可回看/导出崩溃。
 * - 启动时预载历史崩溃记录到缓冲，进程崩溃后重启仍可见。
 * - 仍链式调用原始 handler，不改变系统默认崩溃行为（如后续上报通道）。
 */
object CrashReporter {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_LINES = 200
    private val TS_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun install(context: android.content.Context) {
        val file = File(context.filesDir, FILE_NAME)
        // 预载已有崩溃到 AppLogger（重启后仍可查看）。
        runCatching {
            file.readLines().takeLast(MAX_LINES).forEach { AppLogger.i("CrashLog", it) }
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val ts = TS_FMT.format(Date())
            val stack = Log.getStackTraceString(throwable)
            val record = "$ts ${throwable.javaClass.name}: ${throwable.message}\n$stack"
            runCatching {
                file.appendText("$record\n${"-".repeat(40)}\n")
                val lines = file.readLines()
                if (lines.size > MAX_LINES) file.writeText(lines.takeLast(MAX_LINES).joinToString("\n"))
            }
            AppLogger.e("Crash", "${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
