package com.tickclear.app.ui.tasks

import app.cash.turbine.test
import com.tickclear.app.domain.repository.RecycleBinRepository
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.domain.usecase.RestoreGroupCascadeUseCase
import com.tickclear.app.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * RecycleBinViewModel 单元测试：验证 items 流暴露与 restore/purge 按 type 正确路由。
 * 使用 MainDispatcherRule 使 viewModelScope 可用，turbine 收集 StateFlow，mockk 替身仓库。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecycleBinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val restoreGroupCascade = mockk<RestoreGroupCascadeUseCase>(relaxed = true)

    private fun item(id: String, type: String) =
        RecycleBinItem(id = id, type = type, name = id, deletedAt = 1L)

    @Test
    fun `items 暴露仓库回收站列表`() = runTest {
        val repo = mockk<RecycleBinRepository>(relaxed = true)
        val list = listOf(item("t1", "task"), item("g1", "group"))
        coEvery { repo.observeItems() } returns flowOf(list)

        val vm = RecycleBinViewModel(repo, restoreGroupCascade)

        vm.items.test {
            assertEquals(list, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `restore 按 type 路由到任务或分组`() = runTest {
        val repo = mockk<RecycleBinRepository>(relaxed = true)
        coEvery { repo.observeItems() } returns flowOf(emptyList())
        val vm = RecycleBinViewModel(repo, restoreGroupCascade)

        vm.restore(item("t1", "task"))
        vm.restore(item("g1", "group"))

        coVerify(exactly = 1) { repo.restoreTask("t1") }
        coVerify(exactly = 1) { restoreGroupCascade("g1") }
    }

    @Test
    fun `purge 按 type 路由到任务或分组`() = runTest {
        val repo = mockk<RecycleBinRepository>(relaxed = true)
        coEvery { repo.observeItems() } returns flowOf(emptyList())
        val vm = RecycleBinViewModel(repo, restoreGroupCascade)

        vm.purge(item("t1", "task"))
        vm.purge(item("g1", "group"))

        coVerify(exactly = 1) { repo.purgeTask("t1") }
        coVerify(exactly = 1) { repo.purgeGroup("g1") }
    }

    @Test
    fun `purgeAll 强制清理全部软删记录`() = runTest {
        val repo = mockk<RecycleBinRepository>(relaxed = true)
        coEvery { repo.observeItems() } returns flowOf(emptyList())
        val vm = RecycleBinViewModel(repo, restoreGroupCascade)

        vm.purgeAll()

        coVerify(exactly = 1) { repo.purgeExpired(any()) }
    }
}
