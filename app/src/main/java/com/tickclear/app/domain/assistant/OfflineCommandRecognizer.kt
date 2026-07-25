package com.tickclear.app.domain.assistant

import com.tickclear.app.domain.model.Task

/**
 * 离线热词指令解析（V2.42，纯函数、零依赖、可单测）。
 *
 * 不引入任何端侧推理框架，仅基于关键词匹配，把系统 [android.speech.SpeechRecognizer]
 * 识别出的中文文本转换为结构化指令（暂停 / 启用 / 删除 + 可选目标任务名）。
 *
 * 设计取舍：
 * - 命中「动作 + 任务名」才执行；删除仅在任务名能匹配到真实任务时才生效，避免误删；
 * - 系统 ASR 不可用时由上层 best-effort 降级（提示改用文本指令），本解析器本身不感知 ASR 可用性。
 */
sealed interface OfflineCommand {
    /** 无法识别为任何已知指令。 */
    data object Unknown : OfflineCommand

    /** 暂停目标任务（可逆）。[keyword] 为任务名片段，可能为空（需上层提示补全）。 */
    data class Pause(val keyword: String?) : OfflineCommand

    /** 启用/恢复目标任务（可逆）。[keyword] 同上。 */
    data class Resume(val keyword: String?) : OfflineCommand

    /** 删除目标任务（危险操作，仅当 [keyword] 能匹配真实任务时才应执行）。 */
    data class Delete(val keyword: String?) : OfflineCommand
}

/** 离线指令动作枚举（回显文案见 strings.xml 的 offline_action_*）。 */
enum class OfflineAction {
    PAUSE,
    RESUME,
    DELETE,
}

object OfflineCommandRecognizer {

    private val PAUSE_WORDS = listOf("暂停", "停一下", "先停", "放下", "挂起", "暂缓", "停掉", "暂停一下")
    private val RESUME_WORDS = listOf("启用", "继续", "恢复", "重启", "重新开始")
    private val DELETE_WORDS = listOf("删除", "删掉", "删了", "移除", "去掉", "清除", "干掉")
    private val CONNECTORS = listOf("把", "给", "这个", "那个", "一下", "吧", "请", "帮我", "帮我把", "我要", "我想")

    /**
     * 解析识别文本为指令。
     * - 文本为空或不含任何动作词 → [OfflineCommand.Unknown]；
     * - 含动作词 → 对应动作 + 去除动作/连接词后的剩余片段作为 [keyword]（空串归一为 null）。
     */
    fun parse(text: String): OfflineCommand {
        val raw = text.trim()
        if (raw.isEmpty()) return OfflineCommand.Unknown
        val kind = detectAction(raw) ?: return OfflineCommand.Unknown
        val keyword = extractKeyword(raw, kind)
        return when (kind) {
            ActionKind.PAUSE -> OfflineCommand.Pause(keyword)
            ActionKind.RESUME -> OfflineCommand.Resume(keyword)
            ActionKind.DELETE -> OfflineCommand.Delete(keyword)
        }
    }

    /**
     * 在候选任务中按 [keyword] 匹配目标任务（仅未软删任务）。
     * 优先级：精确标题 > 前缀 > 包含（忽略大小写）。无 keyword 或无匹配返回 null。
     */
    fun matchTask(tasks: List<Task>, keyword: String?): Task? {
        val k = keyword?.trim().orEmpty()
        if (k.isEmpty()) return null
        val candidates = tasks.filter { it.deletedAt == null && it.title.isNotEmpty() }
        return candidates.firstOrNull { it.title == k }
            ?: candidates.firstOrNull { it.title.startsWith(k) }
            ?: candidates.firstOrNull { it.title.contains(k, ignoreCase = true) }
    }

    private enum class ActionKind { PAUSE, RESUME, DELETE }

    private fun detectAction(text: String): ActionKind? =
        when {
            // 删除优先：文本同时含「删除」与「暂停/启用」词时（如「删除暂停中的X」），以删除为准，避免误判为暂停。
            DELETE_WORDS.any { text.contains(it) } -> ActionKind.DELETE
            PAUSE_WORDS.any { text.contains(it) } -> ActionKind.PAUSE
            RESUME_WORDS.any { text.contains(it) } -> ActionKind.RESUME
            else -> null
        }

    private fun extractKeyword(text: String, kind: ActionKind): String? {
        val words = when (kind) {
            ActionKind.PAUSE -> PAUSE_WORDS
            ActionKind.RESUME -> RESUME_WORDS
            ActionKind.DELETE -> DELETE_WORDS
        } + CONNECTORS
        var s = text
        for (w in words) s = s.replace(w, "")
        s = s.replace("\\s+".toRegex(), "").trim()
        return if (s.isEmpty()) null else s
    }
}
