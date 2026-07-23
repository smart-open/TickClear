package com.tickclear.app.domain.backup

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.TaskRepository
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

        val bm = BackupManager(taskRepo, groupRepo, completionRepo, checkInRepo, medalRepo)
        val json = bm.exportToJson()

        // 导出必须包含易遗漏的字段（子日级重复 + 位置提醒）
        val exported = JSONObject(json).getJSONArray("tasks").getJSONObject(0)
        assertTrue("repeatIntervalHours 应被导出", exported.has("repeatIntervalHours"))
        assertEquals(8, exported.getInt("repeatIntervalHours"))
        assertTrue("geoLat 应被导出", exported.has("geoLat"))
        assertEquals(31.2304, exported.getDouble("geoLat"), 0.0001)

        // 导入后 taskRepository.upsert 的参数应保留这些字段
        bm.importFromJson(json)
        val slot = slot<Task>()
        coVerify(exactly = 1) { taskRepo.upsert(capture(slot)) }
        val imported = slot.captured
        assertEquals(8, imported.repeatIntervalHours)
        assertEquals(31.2304, imported.geoLat!!, 0.0001)
        assertEquals(200, imported.geoRadius)
    }
}
