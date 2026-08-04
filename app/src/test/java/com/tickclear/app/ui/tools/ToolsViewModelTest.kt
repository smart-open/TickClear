package com.tickclear.app.ui.tools

import app.cash.turbine.test
import com.tickclear.app.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ToolsViewModel] 常用工具置顶行为测试。
 * 用 mockk 模拟 SettingsRepository，避免在 JVM 单测里引入 DataStore 的 Android 依赖。
 *
 * 注意：`favorites` 是 `stateIn(WhileSubscribed)` 包装的，必须有订阅者
 * 上游才会启动；测试用 turbine.test{} 触发订阅，断言结束后释放。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: SettingsRepository
    private val favoritesFlow = MutableStateFlow<List<String>>(emptyList())
    private lateinit var vm: ToolsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.favoriteToolRoutes } returns favoritesFlow.asStateFlow()
        coEvery { repo.setFavoriteToolRoutes(any()) } answers {
            favoritesFlow.value = firstArg()
        }
        vm = ToolsViewModel(repo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始置顶列表为空`() = runTest {
        vm.favorites.test {
            assertEquals(emptyList<String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite 把未置顶路由追加到末尾`() = runTest {
        vm.favorites.test {
            assertEquals(emptyList<String>(), awaitItem())
            vm.toggleFavorite("tools/ruler")
            assertEquals(listOf("tools/ruler"), awaitItem())
            vm.toggleFavorite("tools/noise")
            assertEquals(listOf("tools/ruler", "tools/noise"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite 已置顶则移除且保留其余顺序`() = runTest {
        vm.favorites.test {
            awaitItem() // 初始
            vm.toggleFavorite("tools/ruler")
            awaitItem()
            vm.toggleFavorite("tools/noise")
            awaitItem()
            vm.toggleFavorite("tools/compass")
            awaitItem()
            vm.toggleFavorite("tools/noise")
            assertEquals(listOf("tools/ruler", "tools/compass"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFavoriteToolRoutes 直接覆盖原有列表`() = runTest {
        vm.favorites.test {
            awaitItem()
            vm.toggleFavorite("tools/a")
            awaitItem()
            vm.toggleFavorite("tools/b")
            awaitItem()
            repo.setFavoriteToolRoutes(listOf("tools/c"))
            assertEquals(listOf("tools/c"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `同一路由重复 toggle 后净效果为零`() = runTest {
        vm.favorites.test {
            awaitItem()
            vm.toggleFavorite("tools/x")
            awaitItem()
            vm.toggleFavorite("tools/x")
            val final = awaitItem()
            assertTrue("tools/x" !in final)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite 写入最终落到 DataStore`() = runTest {
        vm.toggleFavorite("tools/y")
        coVerify(exactly = 1) { repo.setFavoriteToolRoutes(listOf("tools/y")) }
    }

    @Test
    fun `空字符串路由不应进入列表`() = runTest {
        vm.favorites.test {
            awaitItem()
            vm.toggleFavorite("")
            // 初始已是空，再 toggle "" 仍是空 → 仅应看到一次初始事件
            // 但此处应保证没有任何 setFavoriteToolRoutes 被调用过
            coVerify(exactly = 0) { repo.setFavoriteToolRoutes(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }
}