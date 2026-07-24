package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 组级暂停/启用/删除级联（V2.33）单元测试：用 mockk 替身仓库验证级联落到子任务。
 */
class GroupUseCasesTest {

    private fun task(id: String, groupId: String, status: Int = 0) = Task(
        id = id, title = id, groupId = groupId, status = status, deletedAt = null,
    )

    @Test
    fun `pause group cascades to children`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val children = listOf(task("t1", "g1"), task("t2", "g1"))
        coEvery { taskRepo.observeByGroup("g1") } returns flowOf(children)

        PauseGroupUseCase(taskRepo)("g1")

        coVerify { taskRepo.setStatus("t1", TaskStatus.PAUSED, null) }
        coVerify { taskRepo.setStatus("t2", TaskStatus.PAUSED, null) }
    }

    @Test
    fun `resume group cascades children to active`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val children = listOf(task("t1", "g1", status = TaskStatus.PAUSED.code))
        coEvery { taskRepo.observeByGroup("g1") } returns flowOf(children)

        ResumeGroupUseCase(taskRepo)("g1")

        coVerify { taskRepo.setStatus("t1", TaskStatus.ACTIVE, null) }
    }

    @Test
    fun `delete group cascade soft deletes children then group`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        val groupRepo = mockk<GroupRepository>(relaxed = true)
        val children = listOf(task("t1", "g1"), task("t2", "g1"))
        coEvery { taskRepo.observeByGroup("g1") } returns flowOf(children)

        DeleteGroupCascadeUseCase(groupRepo, taskRepo)("g1")

        coVerify { taskRepo.softDelete("t1") }
        coVerify { taskRepo.softDelete("t2") }
        coVerify { groupRepo.softDelete("g1") }
    }

    @Test
    fun `pause task sets paused status`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        PauseTaskUseCase(taskRepo)(task("t1", "g1"))
        coVerify { taskRepo.setStatus("t1", TaskStatus.PAUSED, null) }
    }

    @Test
    fun `resume task sets active status`() = runTest {
        val taskRepo = mockk<TaskRepository>(relaxed = true)
        ResumeTaskUseCase(taskRepo)(task("t1", "g1", status = TaskStatus.PAUSED.code))
        coVerify { taskRepo.setStatus("t1", TaskStatus.ACTIVE, null) }
    }
}
