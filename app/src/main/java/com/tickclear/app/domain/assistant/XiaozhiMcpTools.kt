package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.usecase.AddTaskUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 小智 MCP 工具集：当前实现 create_task，将对话中的意图落库为任务。 */
@Singleton
class XiaozhiMcpTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addTaskUseCase: AddTaskUseCase,
) {
    private companion object {
        const val TAG = "XiaozhiMcpTools"
    }

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
    suspend fun handle(call: XiaozhiEvent.McpToolCall): Task? =
        // V2.8X++：部分服务端/固件会给工具名加命名空间前缀（如 "self.create_task"），
        // 按去前缀后的短名匹配，避免真调用被误判为未知工具。
        when (call.tool.substringAfterLast('.')) {
            "create_task" -> buildDraft(call.arguments)
            else -> null
        }

    /** 解析工具参数并构建草稿 [Task]（不含提交/落库）。 */
    fun buildDraft(args: Map<String, Any?>): Task {
        val title = ((args["title"] as? String)?.trim()).orEmpty().ifEmpty { context.getString(R.string.task_default_title) }
        // 钳制分钟到 [0,1439]，越界值（如解析异常的大数）归一，避免非法时间点写入。
        val minute = (args["minute"] as? Number)?.toInt()?.coerceIn(0, 1439)
        // 未知 repeatType 归一为 NONE（白名单校验），杜绝静默落库非法重复类型。
        val repeat = RepeatType.fromCode((args["repeatType"] as? String) ?: "NONE")
        // V2.8X++：LLM 偶尔漏传 date（schema 虽标 required 但不强制）——仅对「非重复且有具体时刻」兜底：
        // 时刻未过 → 今天；已过 → 明天。否则任务会落成「无日期随时任务」，
        // 既不排定点闹钟、今日页也无时间显示，用户体感"说了没建"。
        // 重复任务（DAILY/WEEKLY/…）本就不依赖 date；无时刻则保持 null（随时任务语义）。
        val dateStr = (args["date"] as? String)?.takeIf { it.isNotBlank() }
            ?: minute.takeIf { repeat == RepeatType.NONE }?.let {
                val today = LocalDate.now()
                val nowMin = LocalTime.now().let { t -> t.hour * 60 + t.minute }
                (if (it > nowMin) today else today.plusDays(1)).toString()
            }
        // weekdays 仅对 WEEKLY 有意义，其余类型丢弃，保持数据一致。
        val weekdays = if (repeat == RepeatType.WEEKLY) args["weekdays"] as? String? else null
        // 提前量：>0 代表提前 N 分钟提醒（V2.15 口语「提前15分钟」等）。
        val offset = (args["reminderOffset"] as? Number)?.toInt()?.takeIf { it > 0 }

        // V2.8X++ 深度修复「语音建任务到点不提醒」：
        // 旧逻辑 reminderEnabled = minute != null || offset != null —— 只要 LLM 没回传具体分钟
        // （绝大多数口语「提醒我明天买菜」都无明确时刻）或没传提前量，reminderEnabled 即 false，
        // AddTaskUseCase 据此跳过 scheduleForTask，任务建了却永不排程/通知/响铃/震动。
        // 修正：① 有日程（有日期的当日任务 / 重复任务 / 带提前量）即视为需提醒；
        // ② 无具体分钟但有日程时回落到上午 9:00，保证能生成带时刻的实例
        //    （scheduleForTask 内 inst.dueMinute 为 null 会直接 return，缺 minute 永不触发）；
        // ③ 语音「提醒我…」默认 high 级（自带 CC0 提示音 + 震动），确保到点真有铃声与震动。
        val hasSchedule = dateStr != null || repeat != RepeatType.NONE || offset != null
        val effectiveMinute = minute ?: if (hasSchedule) 9 * 60 else null
        val reminderEnabled = effectiveMinute != null || offset != null

        return Task(
            id = "xz_${UUID.randomUUID()}",
            title = title,
            scheduledDate = dateStr,
            scheduledStartMin = effectiveMinute,
            repeatType = repeat.code,
            repeatWeekdays = weekdays,
            reminderEnabled = reminderEnabled,
            reminderOffsetMin = offset,
            reminderLevel = if (reminderEnabled) "high" else "mid",
            source = "xiaozhi",
        )
    }

    /** 将草稿提交落库（用户已在确认卡点击确认）。 */
    suspend fun commit(task: Task): ToolResult {
        // V2.8X++：提醒排程已下沉到 AddTaskUseCase 统一兜底（落库后自动 cancel+schedule），
        // 此处无需再手动调 ReminderScheduler——语音创建的任务到点即响系统闹钟。
        val res = addTaskUseCase(task)
        // 落库诊断：出现"说了没建"时 logcat 过滤本标签即可确认任务是否真实入库及关键字段。
        AppLogger.d(
            TAG,
            "commit 落库 id=${task.id} title=${task.title} date=${task.scheduledDate} " +
                "minute=${task.scheduledStartMin} repeat=${task.repeatType} reminder=${task.reminderEnabled} " +
                "conflicts=${res.conflicts.size}",
        )
        val note = if (res.conflicts.isNotEmpty()) context.getString(R.string.xiaozhi_task_conflict_note) else ""
        return ToolResult(true, context.getString(R.string.xiaozhi_task_created, task.title, note), task.title)
    }

    /** 未知工具的用户可见提示（供传输层回执/展示复用）。 */
    fun unknownToolMessage(tool: String): String = context.getString(R.string.xiaozhi_unknown_tool, tool)
}
