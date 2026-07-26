package com.tickclear.app.domain.backup

import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
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
        every { habitRepo.observeHabits() } returns flowOf(emptyList())

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
        val bm = BackupManager(taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo, txn)
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
        val bm = BackupManager(taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo, habitRepo, txn)
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
}

/** 测试替身：记录 [run] 是否被调用，并直接执行 block（验证 R5 事务接线）。 */
private class TestTransactionRunner : TransactionRunner {
    var ran = false
    override suspend fun <T> run(block: suspend () -> T): T {
        ran = true
        return block()
    }
}
