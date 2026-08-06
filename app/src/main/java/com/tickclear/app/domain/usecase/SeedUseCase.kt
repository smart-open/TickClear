package com.tickclear.app.domain.usecase

import android.content.Context
import com.tickclear.app.R
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动种子：注入示例任务组与若干示例任务，让用户首屏即有内容可操作。
 * 通过 DataStore 的 [SettingsRepository.firstRunDone] 保证只注入一次。
 */
@Singleton
class SeedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = ensureSeeded()

    suspend fun ensureSeeded() {
        if (settingsRepository.firstRunDone.first()) return

        val gMorning = "seed_g_morning"
        val gHealth = "seed_g_health"
        val gWork = "seed_g_work"

        // 组名/任务名均为用户首屏可见文案，一律经 strings.xml 取值（红线：源码禁硬编码中文）。
        val groups = listOf(
            TaskGroup(gMorning, string(R.string.seed_group_morning), "🌅", "blue", 0),
            TaskGroup(gHealth, string(R.string.seed_group_health), "💊", "mint", 1),
            TaskGroup(gWork, string(R.string.seed_group_work), "💼", "violet", 2),
        )
        groups.forEach { groupRepository.upsert(it) }

        val tasks = listOf(
            task(string(R.string.seed_task_water), gMorning, 7 * 60 + 30, 8 * 60, "DAILY", reminderLevel = "mid"),
            task(string(R.string.seed_task_stretch), gMorning, 7 * 60 + 45, 8 * 60 + 15, "DAILY"),
            task(string(R.string.seed_task_vitamin), gHealth, 8 * 60, 8 * 60 + 30, "DAILY", reminderEnabled = true, reminderLevel = "mid"),
            task(string(R.string.seed_task_blood_pressure), gHealth, 8 * 60 + 30, 9 * 60, "DAILY", reminderEnabled = true, reminderLevel = "high"),
            task(string(R.string.seed_task_todo), gWork, 9 * 60, 9 * 60 + 30, "DAILY"),
            task(string(R.string.seed_task_walk), gWork, 12 * 60 + 30, 13 * 60, "DAILY"),
            task(string(R.string.seed_task_read), null, 21 * 60, 21 * 60 + 30, "DAILY"),
            // 一次性今日任务：今天 19:00 给妈妈打电话
            task(string(R.string.seed_task_call_mom), null, 19 * 60, 19 * 60 + 30, "NONE",
                scheduledDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                reminderEnabled = true, reminderLevel = "high"),
        )
        tasks.forEach { taskRepository.upsert(it) }

        settingsRepository.setFirstRunDone(true)
    }

    private fun string(resId: Int): String = context.getString(resId)

    private fun task(
        title: String,
        groupId: String?,
        startMin: Int,
        endMin: Int,
        repeatType: String,
        scheduledDate: String? = null,
        reminderEnabled: Boolean = false,
        reminderLevel: String = "mid",
    ): Task = Task(
        // 确定性 id：种子任务用稳定标识，首次注入若中途崩溃重跑时以 REPLACE upsert 覆盖，
        // 避免每次重跑生成全新 UUID 导致重复示例数据。
        id = "seed_t_$title",
        groupId = groupId,
        title = title,
        notes = "",
        status = 0,
        scheduledStartMin = startMin,
        scheduledEndMin = endMin,
        allDay = false,
        scheduledDate = scheduledDate,
        repeatType = repeatType,
        repeatIntervalDays = null,
        repeatWeekdays = null,
        repeatMonthDay = null,
        repeatAnchorMin = startMin,
        repeatAnchorDate = null,
        reminderEnabled = reminderEnabled,
        reminderLevel = reminderLevel,
        reminderOffsetMin = null,
        source = "manual",
        completedAt = null,
        deletedAt = null,
    )
}
