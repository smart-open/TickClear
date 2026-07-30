package com.tickclear.app.domain.usecase

import android.content.Context
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import com.tickclear.app.domain.scheduler.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * AddTaskUseCase 单元测试：验证「先入库再冲突检测」的业务契约。
 * 使用 mockk 替身 TaskRepository + 真实 ConflictChecker（object，纯逻辑）。
 * V2.8X++：排程已下沉到 UseCase —— ReminderScheduler 为 object，用 mockkObject 打桩隔离
 * （单测无 Android AlarmManager，不打桩会抛异常）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTaskUseCaseTest {

    private val today: String = LocalDate.now().toString()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(ReminderScheduler)
        coJustRun { ReminderScheduler.cancelForTask(any(), any()) }
        coJustRun { ReminderScheduler.scheduleForTask(any(), any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkObject(ReminderScheduler)
    }

    private fun task(id: String, start: Int, end: Int) = Task(
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

        val useCase = AddTaskUseCase(context, repo, ConflictChecker)
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

        val result = AddTaskUseCase(context, repo, ConflictChecker)(newTask)

        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `开启提醒的任务落库后统一排程`() = runTest {
        val repo = mockk<TaskRepository>(relaxed = true)
        val newTask = task("a", 540, 600).copy(reminderEnabled = true)
        coEvery { repo.upsert(any()) } returns Unit
        coEvery { repo.observeAll() } returns flowOf(listOf(newTask))

        AddTaskUseCase(context, repo, ConflictChecker)(newTask)

        // 先撤旧闹钟，再排新闹钟（下沉兜底：任何路径新建任务都排程）
        coVerify(exactly = 1) { ReminderScheduler.cancelForTask(context, newTask.id) }
        coVerify(exactly = 1) { ReminderScheduler.scheduleForTask(context, newTask, any()) }
    }

    @Test
    fun `未开提醒的任务只撤旧闹钟不排程`() = runTest {
        val repo = mockk<TaskRepository>(relaxed = true)
        val newTask = task("a", 540, 600) // reminderEnabled 默认 false
        coEvery { repo.upsert(any()) } returns Unit
        coEvery { repo.observeAll() } returns flowOf(listOf(newTask))

        AddTaskUseCase(context, repo, ConflictChecker)(newTask)

        coVerify(exactly = 1) { ReminderScheduler.cancelForTask(context, newTask.id) }
        coVerify(exactly = 0) { ReminderScheduler.scheduleForTask(any(), any(), any()) }
    }
}
