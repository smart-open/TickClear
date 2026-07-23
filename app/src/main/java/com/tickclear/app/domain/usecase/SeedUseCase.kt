package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动种子：注入示例任务组与若干示例任务，让用户首屏即有内容可操作。
 * 通过 DataStore 的 [SettingsRepository.firstRunDone] 保证只注入一次。
 */
@Singleton
class SeedUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = ensureSeeded()

    suspend fun ensureSeeded() {
        if (settingsRepository.firstRunDone.first()) return

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).let { null }
        val gMorning = "seed_g_morning"
        val gHealth = "seed_g_health"
        val gWork = "seed_g_work"

        val groups = listOf(
            TaskGroupEntity(gMorning, "晨间流程", "🌅", "blue", 0),
            TaskGroupEntity(gHealth, "健康", "💊", "mint", 1),
            TaskGroupEntity(gWork, "工作", "💼", "violet", 2),
        )
        groups.forEach { groupRepository.upsert(it) }

        val tasks = listOf(
            task("喝一杯温水", gMorning, 7 * 60 + 30, 8 * 60, "DAILY", reminderLevel = "mid"),
            task("晨间拉伸", gMorning, 7 * 60 + 45, 8 * 60 + 15, "DAILY"),
            task("吃维生素", gHealth, 8 * 60, 8 * 60 + 30, "DAILY", reminderEnabled = true, reminderLevel = "mid"),
            task("测血压", gHealth, 8 * 60 + 30, 9 * 60, "DAILY", reminderEnabled = true, reminderLevel = "high"),
            task("写今日待办", gWork, 9 * 60, 9 * 60 + 30, "DAILY"),
            task("午间散步", gWork, 12 * 60 + 30, 13 * 60, "DAILY"),
            task("阅读 30 分钟", null, 21 * 60, 21 * 60 + 30, "DAILY"),
            // 一次性今日任务：今天 19:00 给妈妈打电话
            task("给妈妈打电话", null, 19 * 60, 19 * 60 + 30, "NONE",
                scheduledDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                reminderEnabled = true, reminderLevel = "high"),
        )
        tasks.forEach { taskRepository.upsert(it) }

        settingsRepository.setFirstRunDone(true)
    }

    private fun task(
        title: String,
        groupId: String?,
        startMin: Int,
        endMin: Int,
        repeatType: String,
        scheduledDate: String? = null,
        reminderEnabled: Boolean = false,
        reminderLevel: String = "mid",
    ): TaskEntity = TaskEntity(
        id = UUID.randomUUID().toString(),
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
