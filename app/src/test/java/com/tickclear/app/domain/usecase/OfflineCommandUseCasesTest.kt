package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.assistant.OfflineCommand
import com.tickclear.app.domain.assistant.OfflineAction
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.TaskRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ApplyOfflineCommandUseCase 单测（V2.42）。
 * 验证命中任务执行对应动作、未给任务名/未匹配时安全返回、删除不会误伤。
 */
class OfflineCommandUseCasesTest {

    private val taskRepo = mockk<TaskRepository>(relaxed = true)
    private val useCase = ApplyOfflineCommandUseCase(taskRepo)

    private fun tasks() = listOf(
        Task(id = "1", title = "买菜", createdAt = 0L, updatedAt = 0L),
        Task(id = "2", title = "健身", createdAt = 0L, updatedAt = 0L),
    )

    @Test
    fun `暂停命中任务 解析为 Applied 并置 PAUSED`() = runTest {
        val result = useCase(OfflineCommand.Pause("买菜"), tasks())
        assertEquals(OfflineCommandResult.Applied(tasks()[0], OfflineAction.PAUSE), result)
        coVerify(exactly = 1) { taskRepo.setStatus("1", TaskStatus.PAUSED, null) }
    }

    @Test
    fun `启用命中任务 解析为 Applied 并置 ACTIVE`() = runTest {
        val result = useCase(OfflineCommand.Resume("健身"), tasks())
        assertEquals(OfflineCommandResult.Applied(tasks()[1], OfflineAction.RESUME), result)
        coVerify(exactly = 1) { taskRepo.setStatus("2", TaskStatus.ACTIVE, null) }
    }

    @Test
    fun `删除命中任务 解析为 Applied 并软删`() = runTest {
        val result = useCase(OfflineCommand.Delete("买菜"), tasks())
        assertEquals(OfflineCommandResult.Applied(tasks()[0], OfflineAction.DELETE), result)
        coVerify(exactly = 1) { taskRepo.softDelete("1") }
    }

    @Test
    fun `未给任务名 返回 NoTarget 不落库`() = runTest {
        val result = useCase(OfflineCommand.Pause(null), tasks())
        assertEquals(OfflineCommandResult.NoTarget, result)
        coVerify(exactly = 0) { taskRepo.setStatus(any(), any(), any()) }
    }

    @Test
    fun `任务名无匹配 返回 NotFound 删除不误伤`() = runTest {
        val result = useCase(OfflineCommand.Delete("不存在的任务"), tasks())
        assertEquals(OfflineCommandResult.NotFound, result)
        coVerify(exactly = 0) { taskRepo.softDelete(any()) }
    }

    @Test
    fun `无法解析 返回 Unknown`() = runTest {
        assertEquals(OfflineCommandResult.Unknown, useCase(OfflineCommand.Unknown, tasks()))
    }
}
