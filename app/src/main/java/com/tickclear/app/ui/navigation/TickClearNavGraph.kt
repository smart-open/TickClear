package com.tickclear.app.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tickclear.app.ui.RootViewModel
import com.tickclear.app.ui.assistant.AssistantScreen
import com.tickclear.app.ui.settings.AboutScreen
import com.tickclear.app.ui.settings.DebugScreen
import com.tickclear.app.ui.settings.SettingsScreen
import com.tickclear.app.ui.stats.StatsScreen
import com.tickclear.app.ui.tasks.TasksScreen
import com.tickclear.app.ui.tasks.RecycleBinScreen
import com.tickclear.app.ui.theme.TickClearTheme
import com.tickclear.app.ui.today.TodayScreen

@Composable
fun TickClearApp(
    rootViewModel: RootViewModel = hiltViewModel(),
    startAction: String? = null,
    onStartActionConsumed: () -> Unit = {},
) {
    val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val sizeClass = rememberAppSizeClass()

    TickClearTheme(mode = themeMode) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        // V2.9：动态快捷方式启动动作 → 跳转对应目的地（消费后置空，支持重复点击）。
        LaunchedEffect(startAction) {
            val action = startAction ?: return@LaunchedEffect
            when (action) {
                ShortcutHelper.ACTION_NEW_TASK -> navController.navigate("${Routes.TASKS}?openEditor=true") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
                ShortcutHelper.ACTION_ASSISTANT -> navController.navigate(Routes.ASSISTANT) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
                ShortcutHelper.ACTION_TODAY -> navController.navigate(Routes.TODAY) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
            }
            onStartActionConsumed()
        }
        val onNavigate: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        if (sizeClass == AppSizeClass.COMPACT) {
            Scaffold(
                bottomBar = {
                    TickClearBottomBar(currentRoute = currentRoute, onNavigate = onNavigate)
                },
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                TickClearNavRail(currentRoute = currentRoute, onNavigate = onNavigate)
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    isWide = true,
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        modifier = modifier,
    ) {
        composable(Routes.TODAY) {
            TodayScreen(
                onNavigateToAssistant = { navController.navigate(Routes.ASSISTANT) },
                onNavigateToStats = {
                    navController.navigate(Routes.STATS) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                isWide = isWide,
            )
        }
        composable(
            route = "${Routes.TASKS}?openEditor={openEditor}",
            arguments = listOf(
                navArgument("openEditor") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            TasksScreen(
                isWide = isWide,
                onNavigateToRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                initialOpenEditor = entry.arguments?.getBoolean("openEditor") ?: false,
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                isWide = isWide,
                onGoToday = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(Routes.ASSISTANT) { AssistantScreen(isWide = isWide, onBack = { navController.navigate(Routes.TODAY) }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToDebug = { navController.navigate(Routes.DEBUG) },
                onBack = { navController.navigate(Routes.TODAY) },
                isWide = isWide,
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RECYCLE_BIN) {
            RecycleBinScreen(
                onBack = { navController.popBackStack() },
                onNavigateToToday = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
