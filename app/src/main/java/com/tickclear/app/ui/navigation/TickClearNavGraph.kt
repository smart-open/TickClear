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
import com.tickclear.app.ui.settings.VoiceHistoryScreen
import com.tickclear.app.ui.stats.StatsScreen
import com.tickclear.app.ui.tasks.TasksScreen
import com.tickclear.app.ui.tasks.RecycleBinScreen
import com.tickclear.app.ui.habits.HabitsScreen
import com.tickclear.app.ui.theme.TickClearTheme
import com.tickclear.app.ui.today.TodayScreen
import com.tickclear.app.ui.tools.ToolsScreen
import com.tickclear.app.ui.tools.IntervalReminderScreen
import com.tickclear.app.ui.tools.WaterReminderViewModel
import com.tickclear.app.ui.tools.RestReminderViewModel
import com.tickclear.app.ui.tools.EyeCareReminderViewModel
import com.tickclear.app.ui.tools.VoiceMemoScreen
import com.tickclear.app.ui.tools.PasswordVaultScreen
import com.tickclear.app.ui.tools.QrScreen
import com.tickclear.app.ui.tools.NapScreen
import com.tickclear.app.ui.tools.NapViewModel
import com.tickclear.app.ui.tools.ExpiryScreen
import com.tickclear.app.ui.tools.ExpiryViewModel
import com.tickclear.app.ui.tools.HearingScreen
import com.tickclear.app.ui.tools.HearingViewModel
import com.tickclear.app.ui.tools.FlashlightScreen
import com.tickclear.app.ui.tools.RulerScreen
import com.tickclear.app.ui.tools.NoiseMeterScreen
import com.tickclear.app.ui.tools.LotteryScreen
import com.tickclear.app.ui.tools.LotteryViewModel
import com.tickclear.app.ui.tools.VisionSelfTestScreen
import com.tickclear.app.ui.tools.MoodScreen
import com.tickclear.app.ui.tools.MoodViewModel
import com.tickclear.app.ui.tools.PomodoroScreen
import com.tickclear.app.ui.tools.PomodoroViewModel
import com.tickclear.app.ui.tools.TableCalcScreen
import com.tickclear.app.ui.tools.LevelScreen
import com.tickclear.app.ui.tools.WeigherScreen
import com.tickclear.app.ui.tools.ClockOverlayScreen
import com.tickclear.app.ui.tools.CountdownScreen
import com.tickclear.app.ui.tools.CountdownViewModel
import com.tickclear.app.ui.tools.BarcodeScreen
import com.tickclear.app.ui.tools.BarcodeViewModel
import com.tickclear.app.ui.tools.MosaicScreen
import com.tickclear.app.ui.tools.CompassScreen
import com.tickclear.app.ui.tools.BackfillScreen
import com.tickclear.app.ui.tools.BackfillViewModel
import com.tickclear.app.ui.tools.WatermarkScreen
import com.tickclear.app.ui.tools.CameraDetectScreen
import com.tickclear.app.ui.tools.PrivacyDetectViewModel
import com.tickclear.app.ui.tools.ClipboardGuardScreen
import com.tickclear.app.ui.tools.ClipboardGuardViewModel
import com.tickclear.app.ui.tools.ArrivalScreen
import com.tickclear.app.ui.tools.ArrivalViewModel
import com.tickclear.app.ui.tools.BackupExportScreen
import com.tickclear.app.ui.tools.BackupExportViewModel
import com.tickclear.app.ui.tools.PrivacyCheckScreen
import com.tickclear.app.ui.tools.PrivacyCheckViewModel
import com.tickclear.app.ui.tools.LoanCalculatorScreen
import com.tickclear.app.ui.tools.IncomeTaxScreen
import com.tickclear.app.ui.tools.ImageCompressScreen
import com.tickclear.app.ui.tools.ImageGrayscaleScreen
import com.tickclear.app.ui.tools.CookingTimerScreen
import com.tickclear.app.ui.tools.CookingTimerViewModel
import com.tickclear.app.ui.tools.AnimalSoundScreen

@Composable
fun TickClearApp(
    rootViewModel: RootViewModel = hiltViewModel(),
    startAction: String? = null,
    onStartActionConsumed: () -> Unit = {},
) {
    val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
    val themeSkin by rootViewModel.themeSkin.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val sizeClass = rememberAppSizeClass()

    TickClearTheme(mode = themeMode, skin = themeSkin) {
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
                    restoreState = true
                }
            }
            onStartActionConsumed()
        }
        val onNavigate: (String) -> Unit = { route ->
            if (route == Routes.TODAY) {
                // 回「今日」（start destination）：直接弹回栈底，绕开
                // navigate(start) + popUpTo(start){saveState} + restoreState 组合在
                // start destination 上的 no-op 问题（从助手页点「今日」tab 无法切换）。
                val popped = navController.popBackStack(navController.graph.startDestinationId, false)
                if (!popped) {
                    navController.navigate(route) { launchSingleTop = true }
                }
            } else {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
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
                    // 统计详情是「今日」的子页，同工具详情一样必须裸 navigate。
                    // 此处原写法带 popUpTo(start)，因当前栈恰为 [TODAY] 而退化为 no-op 未暴露问题，
                    // 但属同一错误模式，统一规范掉，避免后续栈形状变化时重现「返回跳回今日」。
                    navController.navigate(Routes.STATS) { launchSingleTop = true }
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
        composable(Routes.HABITS) {
            HabitsScreen(isWide = isWide)
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
        composable(
            // V2.8X++：openConfig=true 时进入助手页直接弹出配置面板（设置 Tab「助手配置」直达入口），
            // 复用 TASKS?openEditor 的参数化路由模式；默认 false，普通导航行为不变。
            route = "${Routes.ASSISTANT}?openConfig={openConfig}",
            arguments = listOf(
                navArgument("openConfig") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            AssistantScreen(
                isWide = isWide,
                // 回「今日」用 popBackStack 弹回栈底，避免 navigate(TODAY) 堆叠重复 entry 导致脏栈。
                onBack = { navController.popBackStack(navController.graph.startDestinationId, false) },
                initialOpenConfig = entry.arguments?.getBoolean("openConfig") ?: false,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToDebug = { navController.navigate(Routes.DEBUG) },
                onNavigateToVoiceHistory = { navController.navigate(Routes.VOICE_HISTORY) },
                onNavigateToAssistantConfig = {
                    navController.navigate("${Routes.ASSISTANT}?openConfig=true") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack(navController.graph.startDestinationId, false) },
                isWide = isWide,
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.VOICE_HISTORY) {
            VoiceHistoryScreen(onBack = { navController.popBackStack() })
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

        // V2.9 工具箱：统计 Tab 改造为工具箱（统计详情仍经 Today 进度环进入 Routes.STATS）
        composable(Routes.TOOLS) {
            ToolsScreen(
                onNavigate = { route ->
                    // 工具详情是工具箱的子页，必须保留 TOOLS 在回退栈上，返回才能回到工具列表。
                    // 禁止套用底部 Tab 切换的 popUpTo(start){saveState}+restoreState 组合：
                    // 那会把 TOOLS 从栈上弹掉，栈变成 [TODAY, 工具详情]，返回直接跳回「今日」。
                    // launchSingleTop 仅用于防止快速连点重复压入同一详情页。
                    navController.navigate(route) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.TOOLS_WATER) {
            IntervalReminderScreen(
                vm = hiltViewModel<WaterReminderViewModel>(),
                onBack = { navController.popBackStack() },
                isWide = isWide,
            )
        }
        composable(Routes.TOOLS_REST) {
            IntervalReminderScreen(
                vm = hiltViewModel<RestReminderViewModel>(),
                onBack = { navController.popBackStack() },
                isWide = isWide,
            )
        }
        composable(Routes.TOOLS_EYECARE) {
            IntervalReminderScreen(
                vm = hiltViewModel<EyeCareReminderViewModel>(),
                onBack = { navController.popBackStack() },
                isWide = isWide,
            )
        }
        composable(Routes.TOOLS_VOICE) {
            VoiceMemoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_VAULT) {
            PasswordVaultScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_QR) {
            QrScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_NAP) {
            NapScreen(
                vm = hiltViewModel<NapViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_EXPIRY) {
            ExpiryScreen(
                vm = hiltViewModel<ExpiryViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_HEARING) {
            HearingScreen(
                vm = hiltViewModel<HearingViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_FLASHLIGHT) {
            FlashlightScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_RULER) {
            RulerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_NOISE) {
            NoiseMeterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_LOTTERY) {
            LotteryScreen(
                vm = hiltViewModel<LotteryViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_VISION) {
            VisionSelfTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_MOOD) {
            MoodScreen(
                vm = hiltViewModel<MoodViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_POMODORO) {
            PomodoroScreen(
                vm = hiltViewModel<PomodoroViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_CALC) {
            TableCalcScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_LEVEL) {
            LevelScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_SCALE) {
            WeigherScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_CLOCK_OVERLAY) {
            ClockOverlayScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_COUNTDOWN) {
            CountdownScreen(
                vm = hiltViewModel<CountdownViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_BARCODE) {
            BarcodeScreen(
                vm = hiltViewModel<BarcodeViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_MOSAIC) {
            MosaicScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_COMPASS) {
            CompassScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_BACKFILL) {
            BackfillScreen(
                vm = hiltViewModel<BackfillViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_WATERMARK) {
            WatermarkScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_CAMERA_DETECT) {
            CameraDetectScreen(
                viewModel = hiltViewModel<PrivacyDetectViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_CLIPBOARD_GUARD) {
            ClipboardGuardScreen(
                viewModel = hiltViewModel<ClipboardGuardViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_ARRIVAL) {
            ArrivalScreen(
                viewModel = hiltViewModel<ArrivalViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_BACKUP) {
            BackupExportScreen(
                viewModel = hiltViewModel<BackupExportViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_PRIVACY) {
            PrivacyCheckScreen(
                viewModel = hiltViewModel<PrivacyCheckViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_LOAN) {
            LoanCalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_TAX) {
            IncomeTaxScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_IMG_COMPRESS) {
            ImageCompressScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_IMG_GRAY) {
            ImageGrayscaleScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOLS_COOK_TIMER) {
            CookingTimerScreen(
                vm = hiltViewModel<CookingTimerViewModel>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TOOLS_ANIMAL) {
            AnimalSoundScreen(onBack = { navController.popBackStack() })
        }
    }
}
