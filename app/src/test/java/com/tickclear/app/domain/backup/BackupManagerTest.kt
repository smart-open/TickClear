package com.tickclear.app.domain.backup

import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.model.Task
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.ExpiryRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.HabitRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份/恢复往返测试：校验易遗漏字段（子日级重复 repeatIntervalHours、位置提醒 geo*）
 * 在导出→导入后保持完整，避免静默数据丢失。
 */
class BackupManagerTest {

    @Test
    fun `export then import preserves geo and sub-day repeat fields`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val groupRepo = mockk<GroupRepository>(relaxed = true)
        val completionRepo = mockk<CompletionRepository>(relaxed = true)
        val checkInRepo = mockk<CheckInRepository>(relaxed = true)
        val medalRepo = mockk<MedalRepository>(relaxed = true)
        val habitRepo = mockk<HabitRepository>(relaxed = true)
        val expiryRepo = mockk<ExpiryRepository>(relaxed = true)
        val instanceRepo = mockk<TaskInstanceRepository>(relaxed = true)
        every { habitRepo.observeHabits() } returns flowOf(emptyList())
        every { expiryRepo.observeAll() } returns flowOf(emptyList())

        val task = Task(
            id = "t1",
            title = "吃药",
            repeatType = "INTERVAL",
            repeatIntervalHours = 8,
            geoLat = 31.2304,
            geoLng = 121.4737,
            geoRadius = 200,
        )
        every { taskRepo.observeAll() } returns flowOf(listOf(task))
        every { groupRepo.observeActive() } returns flowOf(emptyList())
        every { completionRepo.observeAll() } returns flowOf(emptyList())

        val txn = TestTransactionRunner()
        val bm = BackupManager(taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo, txn, expiryRepo, instanceRepo)
        val json = bm.exportToJson()

        // 导出必须包含易遗漏的字段（子日级重复 + 位置提醒）
        val exported = JSONObject(json).getJSONArray("tasks").getJSONObject(0)
        assertTrue("repeatIntervalHours 应被导出", exported.has("repeatIntervalHours"))
        assertEquals(8, exported.getInt("repeatIntervalHours"))
        assertTrue("geoLat 应被导出", exported.has("geoLat"))
        assertEquals(31.2304, exported.getDouble("geoLat"), 0.0001)

        // 导入后 taskRepository.upsert 的参数应保留这些字段
        bm.importFromJson(json)
        assertTrue("导入应在数据库事务中执行（R5 接线）", txn.ran)
        val slot = slot<Task>()
        coVerify(exactly = 1) { taskRepo.upsert(capture(slot)) }
        val imported = slot.captured
        assertEquals(8, imported.repeatIntervalHours)
        assertEquals(31.2304, imported.geoLat!!, 0.0001)
        assertEquals(200, imported.geoRadius)
    }

    @Test
    fun `export then import preserves habits and checkins`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val groupRepo = mockk<GroupRepository>(relaxed = true)
        val completionRepo = mockk<CompletionRepository>(relaxed = true)
        val checkInRepo = mockk<CheckInRepository>(relaxed = true)
        val medalRepo = mockk<MedalRepository>(relaxed = true)
        val habitRepo = mockk<HabitRepository>(relaxed = true)
        val expiryRepo = mockk<ExpiryRepository>(relaxed = true)
        val instanceRepo = mockk<TaskInstanceRepository>(relaxed = true)
        every { expiryRepo.observeAll() } returns flowOf(emptyList())
        every { taskRepo.observeAll() } returns flowOf(emptyList())
        every { groupRepo.observeActive() } returns flowOf(emptyList())
        every { completionRepo.observeAll() } returns flowOf(emptyList())

        val habit = Habit(
            id = "h1", title = "喝水", emoji = "💧",
            repeatDays = "1,2,3", reminderMin = 9, colorIndex = 2,
        )
        every { habitRepo.observeHabits() } returns flowOf(listOf(habit))
        coEvery { habitRepo.getCheckinDates("h1") } returns listOf("2026-07-25", "2026-07-26")

        val txn = TestTransactionRunner()
        val bm = BackupManager(taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo, txn, expiryRepo, instanceRepo)
        val json = bm.exportToJson()

        val exported = JSONObject(json)
        assertTrue("导出应含 habits 数组（V2.70 设备间迁移）", exported.has("habits"))
        assertEquals(1, exported.getJSONArray("habits").length())
        assertEquals("喝水", exported.getJSONArray("habits").getJSONObject(0).getString("title"))
        assertEquals(2, exported.getJSONArray("habitCheckIns").length())

        bm.importFromJson(json)
        val habitSlot = slot<Habit>()
        coVerify(exactly = 1) { habitRepo.createHabit(capture(habitSlot)) }
        assertEquals("h1", habitSlot.captured.id)
        assertEquals("喝水", habitSlot.captured.title)
        val dates = mutableListOf<String>()
        coVerify(exactly = 2) { habitRepo.checkIn("h1", capture(dates)) }
        assertEquals(2, dates.size)
    }

    /**
     * v2 新增两张表的往返：到期提醒是纯用户数据，重复任务的完成态只存在实例表里，
     * 两者此前都不在备份中，换机后会静默丢失。
     */
    @Test
    fun `导出导入应覆盖到期提醒与任务实例完成态`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val groupRepo = mockk<GroupRepository>(relaxed = true)
        val completionRepo = mockk<CompletionRepository>(relaxed = true)
        val checkInRepo = mockk<CheckInRepository>(relaxed = true)
        val medalRepo = mockk<MedalRepository>(relaxed = true)
        val habitRepo = mockk<HabitRepository>(relaxed = true)
        val expiryRepo = mockk<ExpiryRepository>(relaxed = true)
        val instanceRepo = mockk<TaskInstanceRepository>(relaxed = true)

        val task = Task(id = "t1", title = "吃药", repeatType = "DAILY")
        every { taskRepo.observeAll() } returns flowOf(listOf(task))
        every { groupRepo.observeActive() } returns flowOf(emptyList())
        every { completionRepo.observeAll() } returns flowOf(emptyList())
        every { habitRepo.observeHabits() } returns flowOf(emptyList())
        every { expiryRepo.observeAll() } returns flowOf(
            listOf(
                ExpiryEntity(
                    id = 7L, title = "会员", category = "订阅",
                    expireEpochDay = 20500L, note = "自动续费",
                    reminderEnabled = true, reminderDaysBefore = 3,
                    recurring = true, createdAt = 1000L,
                ),
            ),
        )
        coEvery { instanceRepo.allWithState() } returns listOf(
            TaskInstanceEntity(
                id = "t1@2026-07-25", taskId = "t1", dueDateLocal = "2026-07-25",
                dueMinute = 480, status = 2, completedAt = 2000L, createdAt = 1500L,
            ),
        )

        val bm = BackupManager(
            taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo,
            TestTransactionRunner(), expiryRepo, instanceRepo,
        )
        val json = bm.exportToJson()
        val exported = JSONObject(json)
        assertEquals(2, exported.getInt("schemaVersion"))
        assertEquals(1, exported.getJSONArray("expiries").length())
        assertEquals(1, exported.getJSONArray("taskInstances").length())

        val result = bm.importFromJson(json)
        assertEquals(1, result.expiries)
        assertEquals(1, result.taskInstances)

        val expirySlot = slot<ExpiryEntity>()
        coVerify(exactly = 1) { expiryRepo.insert(capture(expirySlot)) }
        assertEquals("会员", expirySlot.captured.title)
        assertEquals(3, expirySlot.captured.reminderDaysBefore)
        assertTrue("每年重复标记应保留", expirySlot.captured.recurring)

        // 实例必须走 restore（REPLACE），否则本地懒生成的空实例会吞掉完成态
        val instSlot = slot<TaskInstanceEntity>()
        coVerify(exactly = 1) { instanceRepo.restore(capture(instSlot)) }
        assertEquals(2, instSlot.captured.status)
        assertEquals(480, instSlot.captured.dueMinute)
        assertEquals(2000L, instSlot.captured.completedAt)
    }

    /** 孤儿实例（taskId 在备份与本地都找不到对应任务）应被丢弃，不污染数据库。 */
    @Test
    fun `导入应丢弃找不到任务的孤儿实例`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val groupRepo = mockk<GroupRepository>(relaxed = true)
        val completionRepo = mockk<CompletionRepository>(relaxed = true)
        val checkInRepo = mockk<CheckInRepository>(relaxed = true)
        val medalRepo = mockk<MedalRepository>(relaxed = true)
        val habitRepo = mockk<HabitRepository>(relaxed = true)
        val expiryRepo = mockk<ExpiryRepository>(relaxed = true)
        val instanceRepo = mockk<TaskInstanceRepository>(relaxed = true)
        every { taskRepo.observeAll() } returns flowOf(emptyList())
        every { groupRepo.observeActive() } returns flowOf(emptyList())
        every { completionRepo.observeAll() } returns flowOf(emptyList())
        every { habitRepo.observeHabits() } returns flowOf(emptyList())
        every { expiryRepo.observeAll() } returns flowOf(emptyList())

        val bm = BackupManager(
            taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo,
            TestTransactionRunner(), expiryRepo, instanceRepo,
        )
        val json = """
            {"app":"TickClear","schemaVersion":2,"exportedAt":0,
             "taskInstances":[{"id":"ghost@2026-01-01","taskId":"ghost",
             "dueDateLocal":"2026-01-01","status":2}]}
        """.trimIndent()

        bm.importFromJson(json)
        coVerify(exactly = 0) { instanceRepo.restore(any()) }
    }
}

/** 测试替身：记录 [run] 是否被调用，并直接执行 block（验证 R5 事务接线）。 */
private class TestTransactionRunner : TransactionRunner {
    var ran = false
    override suspend fun <T> run(block: suspend () -> T): T {
        ran = true
        return block()
    }
}
