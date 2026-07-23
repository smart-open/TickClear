package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * AddTaskUseCase 单元测试：验证「先入库再冲突检测」的业务契约。
 * 使用 mockk 替身 TaskRepository + 真实 ConflictChecker（object，纯逻辑）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTaskUseCaseTest {

    private val today: String = LocalDate.now().toString()

    private fun task(id: String, start: Int, end: Int) = TaskEntity(
        id = id, title = id, status = 0,
        scheduledStartMin = start, scheduledEndMin = end, allDay = false,
        scheduledDate = today, repeatType = "NONE", deletedAt = null,
    )

    @Test
    fun `插入任务并返回冲突列表`() = runTest {
        val repo = mockk<TaskRepository>(relaxed = true)
        val newTask = task("a", 540, 600)      // 09:00-10:00
        val existing = task("b", 570, 630)     // 09:30-10:30，与 a 重叠
        // observeAll 在 upsert 后返回含两者的全量（模拟已入库）
        coEvery { repo.upsert(any()) } returns Unit
        coEvery { repo.observeAll() } returns flowOf(listOf(newTask, existing))

        val useCase = AddTaskUseCase(repo, ConflictChecker)
        val result = useCase(newTask)

        // 必须先入库
        coVerify(exactly = 1) { repo.upsert(newTask) }
        assertEquals(newTask, result.task)
        assertEquals(listOf("b"), result.conflicts.map { it.id })
    }

    @Test
    fun `无冲突时返回空冲突`() = runTest {
        val repo = mockk<TaskRepository>(relaxed = true)
        val newTask = task("a", 540, 600)
        val other = task("c", 720, 780) // 12:00-13:00 不重叠
        coEvery { repo.upsert(any()) } returns Unit
        coEvery { repo.observeAll() } returns flowOf(listOf(newTask, other))

        val result = AddTaskUseCase(repo, ConflictChecker)(newTask)

        assertTrue(result.conflicts.isEmpty())
    }
}
