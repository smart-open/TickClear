package com.tickclear.app.domain.assistant

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.usecase.AddTaskUseCase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 小智 MCP 工具集：当前实现 create_task，将对话中的意图落库为任务。 */
@Singleton
class XiaozhiMcpTools @Inject constructor(
    private val addTaskUseCase: AddTaskUseCase,
) {
    data class ToolResult(
        val ok: Boolean,
        val message: String,
        val taskTitle: String?,
    )

    /**
     * 处理 MCP 工具调用，返回「待确认的草稿任务」。
     * 返回 null 表示未知工具（不进入确认流程）。
     * 注意：此处【不】落库，落库由 [commit] 在用户确认后进行（语音解析确认卡）。
     */
    suspend fun handle(call: XiaozhiEvent.McpToolCall): Task? = when (call.tool) {
        "create_task" -> buildDraft(call.arguments)
        else -> null
    }

    /** 解析工具参数并构建草稿 [Task]（不含提交/落库）。 */
    fun buildDraft(args: Map<String, Any?>): Task {
        val title = ((args["title"] as? String)?.trim()).orEmpty().ifEmpty { "新任务" }
        val dateStr = args["date"] as? String?
        // 钳制分钟到 [0,1439]，越界值（如解析异常的大数）归一，避免非法时间点写入。
        val minute = (args["minute"] as? Number)?.toInt()?.coerceIn(0, 1439)
        // 未知 repeatType 归一为 NONE（白名单校验），杜绝静默落库非法重复类型。
        val repeat = RepeatType.fromCode((args["repeatType"] as? String) ?: "NONE")
        // weekdays 仅对 WEEKLY 有意义，其余类型丢弃，保持数据一致。
        val weekdays = if (repeat == RepeatType.WEEKLY) args["weekdays"] as? String? else null

        return Task(
            id = "xz_${UUID.randomUUID()}",
            title = title,
            scheduledDate = dateStr,
            scheduledStartMin = minute,
            repeatType = repeat.code,
            repeatWeekdays = weekdays,
            reminderEnabled = minute != null,
            reminderLevel = "mid",
            source = "xiaozhi",
        )
    }

    /** 将草稿提交落库（用户已在确认卡点击确认）。 */
    suspend fun commit(task: Task): ToolResult {
        val res = addTaskUseCase(task)
        val note = if (res.conflicts.isNotEmpty()) "（存在时间冲突，已保留）" else ""
        return ToolResult(true, "已创建任务：${task.title}$note", task.title)
    }
}
