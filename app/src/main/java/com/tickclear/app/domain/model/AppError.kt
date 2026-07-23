package com.tickclear.app.domain.model

import android.content.Context
import com.tickclear.app.R

/**
 * 全局错误码体系（P0.7）。
 *
 * 统一约束应用内可预期的失败场景，配合 [AppException] 抛出、并通过 [message] 映射为
 * 用户可读的本地化文案。UI 层只需 catch [AppException] 即可拿到码值与提示，避免散落的
 * try/catch 直接把技术异常暴露给用户。
 */
enum class ErrorCode(val code: String, private val messageRes: Int) {
    UNKNOWN("E0000", R.string.err_unknown),

    // 数据导入/导出（1xxx）
    IMPORT_READ_FAILED("E1001", R.string.err_import_read),
    IMPORT_PARSE_FAILED("E1002", R.string.err_import_parse),
    IMPORT_VERSION_UNSUPPORTED("E1003", R.string.err_import_version),
    IMPORT_EMPTY("E1004", R.string.err_import_empty),
    EXPORT_WRITE_FAILED("E1101", R.string.err_export_write),

    // 任务写操作（2xxx）
    TASK_INVALID_TIME("E2001", R.string.err_task_time),
    TASK_NOT_FOUND("E2002", R.string.err_task_not_found),

    // 助手 / 网络（3xxx）
    ASSISTANT_NOT_CONFIGURED("E3001", R.string.err_assistant_not_configured),
    ASSISTANT_CONNECT_FAILED("E3002", R.string.err_assistant_connect),
    ;

    /** 返回本地化用户提示（带错误码后缀，便于反馈定位）。 */
    fun message(context: Context): String =
        context.getString(messageRes) + " (" + code + ")"
}

/** 携带 [ErrorCode] 的业务异常。cause 保留原始技术异常用于日志。 */
class AppException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
    val detail: String? = null,
) : Exception(errorCode.code + (detail?.let { ": $it" } ?: ""), cause) {

    fun userMessage(context: Context): String = errorCode.message(context)

    companion object {
        /** 将任意异常规整为 AppException：已是则原样返回，否则包为 UNKNOWN。 */
        fun from(t: Throwable, fallback: ErrorCode = ErrorCode.UNKNOWN): AppException =
            t as? AppException ?: AppException(fallback, t)
    }
}
