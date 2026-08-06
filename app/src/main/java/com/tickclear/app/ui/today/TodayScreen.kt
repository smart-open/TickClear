package com.tickclear.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.tickclear.app.ui.components.showTimedSnackbar
import com.tickclear.app.ui.components.ConfettiOverlay
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.components.formatMinute
import com.tickclear.app.domain.model.MedalCatalog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.usecase.TodayItem
import com.tickclear.app.domain.usecase.TodayListPrefs
import com.tickclear.app.ui.components.ConflictBanner
import com.tickclear.app.ui.components.ProgressRing
import com.tickclear.app.ui.components.TaskEditSheet
import com.tickclear.app.ui.components.TaskItem
import com.tickclear.app.ui.stats.StatsContent
import com.tickclear.app.ui.habits.HabitItem
import com.tickclear.app.ui.habits.HabitsViewModel
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    isWide: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 今日页主列表混排：把「今日应打卡」的所有习惯（含已打卡）与「进行中任务」按提醒时间穿插排列。
    val habitsState by habitsViewModel.uiState.collectAsStateWithLifecycle()
    val todayHabits = habitsState.items.filter { it.dueToday }
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val clearConfirmEnabled by viewModel.clearConfirmEnabled.collectAsStateWithLifecycle()
    val ptrState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 由持久化的 id 解析出当前编辑对象（旋转后仍可恢复）
    val editingTask = editingTaskId?.let { id -> state.items.firstOrNull { it.task.id == id }?.task }

    // 软删撤销提示
    val pending = state.pendingDelete
    val snackbarMsg = pending?.let { stringResource(R.string.today_deleted_snack, it.title) } ?: ""
    val clearedSnack = stringResource(R.string.today_cleared_snack)
    val restoreLabel = stringResource(R.string.action_restore)
    val confirmClear: () -> Unit = {
        viewModel.clearAll()
        scope.launch {
            snackbarHostState.showTimedSnackbar(message = clearedSnack)
        }
    }
    LaunchedEffect(pending?.id) {
        if (pending != null) {
            val result = snackbarHostState.showTimedSnackbar(
                message = snackbarMsg,
                actionLabel = restoreLabel,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                SnackbarResult.Dismissed -> viewModel.clearPending()
            }
        }
    }

    // 打卡庆祝：完成时播放撒花+震动；若本次新解锁勋章则提示（medalKeys 非空）。
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    var confettiTrigger by remember { mutableIntStateOf(0) }
    val ctx = LocalView.current.context
    LaunchedEffect(celebration) {
        val ev = celebration ?: return@LaunchedEffect
        Haptic.vibrate(ctx)
        confettiTrigger++
        if (ev.medalKeys.isNotEmpty()) {
            val names = ev.medalKeys.mapNotNull { MedalCatalog.get(it)?.let { m -> ctx.getString(m.nameRes) } }
                .joinToString("、")
            if (names.isNotEmpty()) {
                snackbarHostState.showSnackbar(
                    message = ctx.getString(R.string.medal_unlocked_celebrate, names),
                    duration = SnackbarDuration.Short,
                )
            }
        }
        viewModel.clearCelebration()
    }

    // 方案 C：今日页顶部习惯打卡横条——习惯打上时同样撒花+震动（与计划页习惯段共用同一 ViewModel/事件）。
    val habitsCelebration by habitsViewModel.celebration.collectAsStateWithLifecycle()
    LaunchedEffect(habitsCelebration) {
        val ev = habitsCelebration ?: return@LaunchedEffect
        Haptic.vibrate(ctx)
        confettiTrigger++
        habitsViewModel.clearCelebration()
    }

    // V2.55：编辑中任务被软删（从 state.items 消失）后，关闭编辑页而非让表单回退为「新建」，
    // 避免用户保存时误建重复任务。仅当确实处于「编辑某具体任务」态（editingTaskId 非空）时才触发。
    LaunchedEffect(editingTaskId, showEditor) {
        if (showEditor && editingTaskId != null && editingTask == null) {
            showEditor = false
        }
    }

    val datePattern = stringResource(R.string.today_date_format)
    val dateStr = remember(datePattern) {
        LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern, Locale.CHINESE))
    }
    val greeting = stringResource(greetingRes(java.time.LocalTime.now().hour))
    val ringDesc = stringResource(R.string.a11y_progress_ring_nav)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    // V2.8X 顶栏双行：title slot 默认 Start 对齐，48dp 限制下文字易贴顶，
                    // 用 Box 强制 fillMaxHeight + CenterStart 居中显示。
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column {
                            Text(greeting, style = MaterialTheme.typography.titleMedium)
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val hasIncomplete = state.total > 0 && state.done < state.total
                    IconButton(
                        enabled = hasIncomplete,
                        onClick = {
                            if (clearConfirmEnabled) showClearConfirm = true
                            else confirmClear()
                        },
                    ) {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = stringResource(R.string.today_clear_all_desc),
                        )
                    }
                    ProgressRing(
                        progress = if (state.total > 0) state.done.toFloat() / state.total else 0f,
                        size = 36.dp,
                        stroke = 4.dp,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToStats)
                            .semantics { role = Role.Button; contentDescription = ringDesc }
                            .minimumInteractiveComponentSize(),
                    )
                    IconButton(onClick = onNavigateToAssistant) {
                        Icon(Icons.Filled.SmartToy, contentDescription = stringResource(R.string.tab_assistant))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                TodayMainContent(
                    state = state,
                    listState = listState,
                    isRefreshing = isRefreshing,
                    ptrState = ptrState,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.refreshEncouragement()
                        isRefreshing = false
                    },
                    onComplete = { viewModel.complete(it) },
                    onDelete = { viewModel.delete(it) },
                    onEdit = { editingTaskId = it.task.id; showEditor = true },
                    onAdd = { editingTaskId = null; showEditor = true },
                    shortcutsEnabled = !showEditor && !showClearConfirm,
                    todayHabits = todayHabits,
                    onHabitCheck = { habitsViewModel.toggleToday(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                // 右侧统计侧栏：复用统计主体（独立注入自身 ViewModel）
                StatsContent(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            TodayMainContent(
                state = state,
                listState = listState,
                isRefreshing = isRefreshing,
                ptrState = ptrState,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refreshEncouragement()
                    isRefreshing = false
                },
                onComplete = { viewModel.complete(it) },
                onDelete = { viewModel.delete(it) },
                onEdit = { editingTaskId = it.task.id; showEditor = true },
                onAdd = { editingTaskId = null; showEditor = true },
                    shortcutsEnabled = !showEditor && !showClearConfirm,
                    todayHabits = todayHabits,
                    onHabitCheck = { habitsViewModel.toggleToday(it) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
        }
    }

    ConfettiOverlay(
        trigger = confettiTrigger,
        onFinished = { confettiTrigger = 0 },
    )
    }

    if (showClearConfirm) {
        var dontShow by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.today_clear_all_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.today_clear_all_msg))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .clickable { dontShow = !dontShow },
                    ) {
                        Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                        Text(stringResource(R.string.today_clear_confirm_dont_show))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    if (dontShow) viewModel.setClearConfirmEnabled(false)
                    confirmClear()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showEditor) {
        TaskEditSheet(
            groups = state.groups.values.toList(),
            initial = editingTask,
            onDismiss = { showEditor = false },
            onSave = { viewModel.saveTask(it) },
        )
    }
}

/**
 * 今日主内容（下拉刷新 + 任务列表 + 新建 FAB），窄屏铺满、宽屏作为左栏。
 * listState 由外部持有以在旋转/配置变化后保持滚动位置。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TodayMainContent(
    state: TodayUiState,
    listState: LazyListState,
    isRefreshing: Boolean,
    ptrState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    onRefresh: () -> Unit,
    onComplete: (TodayItem) -> Unit,
    onDelete: (TodayItem) -> Unit,
    onEdit: (TodayItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    shortcutsEnabled: Boolean = true,
    todayHabits: List<HabitItem> = emptyList(),
    onHabitCheck: (String) -> Unit = {},
) {
    // V2.10 键盘快捷键：在 Compose 宿主 View 上挂 OnKeyListener 捕获 ↑↓ 选择 / 空格·回车完成 / N 新建。
    // 采用 View 级监听而非 Modifier.focusable，规避本环境对 focusable 符号的解析差异，且无需强制焦点。
    // V2.8X：初始 focusedIndex = -1（无键盘焦点时不绘制任何「焦点边框」），
    // 避免「今日」首次进入就看到第一项被高亮——用户描述的"任务列表每个边框"实际是默认 focus 边框。
    var focusedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // V2.32：拆分进行中/已完成；已完成数超过阈值时默认折叠（用户可手动展开）。
    // 注意：rememberSaveable / LaunchedEffect 必须在 @Composable 函数体作用域，
    // 不能放在 LazyColumn 的 LazyListScope（仅 item/items 内部是 composable 上下文）。
    val activeItems = state.items.filter { !it.done }
    val doneItems = state.items.filter { it.done }
    // V2.54：键盘焦点只在「进行中」段内移动，故持有 active 列表的最新引用，
    // 让监听闭包始终读取最新 activeItems，避免按合并序号（active+done）索引导致的跨段错位。
    val currentActive = rememberUpdatedState(activeItems)
    var collapseDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (TodayListPrefs.shouldShowCollapseByDoneCount(doneItems.size)) collapseDone = true
    }
    DisposableEffect(view, shortcutsEnabled) {
        val listener = View.OnKeyListener { _, keyCode, event ->
            if (!shortcutsEnabled || event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            // 焦点仅在 active（进行中）段内移动：以 activeItems 本地序号为准，
            // 与 LazyColumn 的 active/done 分段一致，消除高亮与操作目标错位（V2.54）。
            val active = currentActive.value
            val count = active.size
            if (count == 0) return@OnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val next = (focusedIndex + 1).coerceAtMost(count - 1)
                    focusedIndex = next
                    // LazyColumn 第 0 项为鼓励语头部，active 项 j 位于 1 + j
                    scope.launch { listState.scrollToItem(1 + next) }
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val prev = (focusedIndex - 1).coerceAtLeast(0)
                    focusedIndex = prev
                    scope.launch { listState.scrollToItem(1 + prev) }
                    true
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> {
                    // 完成当前聚焦的进行中项
                    val target = active.getOrNull(focusedIndex)
                    if (target != null) {
                        onComplete(target)
                        // 完成聚焦项后 active 数量减一，夹住索引避免越界高亮
                        focusedIndex = focusedIndex.coerceAtMost(active.size - 2).coerceAtLeast(0)
                    }
                    true
                }
                KeyEvent.KEYCODE_N -> {
                    onAdd()
                    true
                }
                else -> false
            }
        }
        view.setOnKeyListener(listener)
        onDispose { view.setOnKeyListener(null) }
    }
    Column(modifier = modifier) {
        if (state.conflictIds.isNotEmpty()) {
            ConflictBanner(count = state.conflictIds.size, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
        }

        // 今日页主列表混排：把「进行中任务」与「今日应打卡习惯」按提醒时间穿插排列。
        // 排序键：任务用 instanceDueMinute（任务模型派生的当日分钟），习惯用 reminderMin（>=0）；
        // 无时间的项 sortKey 为 null，用 Int.MIN_VALUE 排到最上方，避免「有时间项把无时间项挤到底」。
        val allLines: List<TodayLine> = run {
            val taskLines = activeItems.mapIndexed { idx, item ->
                TodayLine.Task(item, state.groups[item.task.groupId], activeIndex = idx)
            }
            val habitLines = todayHabits.map { TodayLine.Habit(it) }
            (taskLines + habitLines).sortedBy { it.sortKey ?: Int.MIN_VALUE }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty() && todayHabits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = Spacing.sm))
                        Text(stringResource(R.string.today_no_task), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.encouragement, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.sm))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    // V2.8X++：contentPadding.top = Spacing.xs，配合下方鼓励语 item 的 vertical = Spacing.xs，
                    // 与 spacedBy(Spacing.xs) 相加得 8dp 上下视觉对称（之前 0 + 8dp 上 / 8dp + 4dp 下 → 上紧下松偏上）。
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = state.encouragement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }

                    // 任务 + 习惯按 sortKey 混排；key 用行唯一标识符，避免 LazyColumn 在重排时复用错位。
                    itemsIndexed(allLines, key = { _, line -> line.key }) { _, line ->
                        Box(modifier = Modifier.animateItem()) {
                            when (line) {
                                is TodayLine.Task -> TaskItem(
                                    item = line.item,
                                    group = line.group,
                                    isConflict = state.conflictIds.contains(line.item.instanceId),
                                    onComplete = { onComplete(line.item) },
                                    onDelete = { onDelete(line.item) },
                                    onEdit = { onEdit(line.item) },
                                    isFocused = line.activeIndex == focusedIndex,
                                    index = line.activeIndex,
                                )
                                is TodayLine.Habit -> HabitRow(
                                    item = line.habit,
                                    onCheck = { onHabitCheck(line.habit.habit.id) },
                                )
                            }
                        }
                    }

                    if (doneItems.isNotEmpty()) {
                        item {
                            DoneSectionHeader(
                                count = doneItems.size,
                                collapsed = collapseDone,
                                showToggle = TodayListPrefs.shouldShowCollapseByDoneCount(doneItems.size),
                                onToggle = { collapseDone = !collapseDone },
                            )
                        }
                        if (!collapseDone) {
                            itemsIndexed(doneItems, key = { _, item -> item.instanceId }) { index, item ->
                                Box(modifier = Modifier.animateItem()) {
                                    TaskItem(
                                        item = item,
                                        group = state.groups[item.task.groupId],
                                        isConflict = state.conflictIds.contains(item.instanceId),
                                        onComplete = { onComplete(item) },
                                        onDelete = { onDelete(item) },
                                        onEdit = { onEdit(item) },
                                        isFocused = false,
                                        index = activeItems.size + 1 + index,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onAdd,
                // 底部新建按钮减小约三分之一（56dp → 40dp），保持圆形。
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg).size(40.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }
}

/** 按当前小时返回问候语资源：早/上午、下午、晚上、深夜。 */
private fun greetingRes(hour: Int): Int = when (hour) {
    in 5..10 -> R.string.today_greeting_morning
    in 11..13 -> R.string.today_greeting_noon
    in 14..17 -> R.string.today_greeting_afternoon
    in 18..22 -> R.string.today_greeting_evening
    else -> R.string.today_greeting_night
}

/**
 * 已完成分区头部（V2.32）：展示已完成数量；当数量超过阈值时提供「折叠/展开」开关。
 */
@Composable
private fun DoneSectionHeader(
    count: Int,
    collapsed: Boolean,
    showToggle: Boolean,
    onToggle: () -> Unit,
) {
    if (showToggle) {
        // stringResource 必须在 composable 上下文预计算，不能在 .semantics {} 内（非 composable 作用域）。
        val headerCd = stringResource(R.string.today_done_section_title, count)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = headerCd }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.today_done_section_title, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(if (collapsed) R.string.today_expand else R.string.today_collapse),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Text(
            text = stringResource(R.string.today_done_section_title, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
    HorizontalDivider()
}

/**
 * 今日习惯行：类型图标（Repeat，与计划 TabRow 一致）+ 圆形打卡勾选 + emoji + 标题 + 提醒时间。
 * reminderMin >= 0 时尾部显示 Schedule 图标 + HH:MM 时间，参考任务行的 timeText 呈现方式。
 * 已打卡时整行 alpha 0.6、勾选图标变实心，与未打卡态视觉区分。
 */
@Composable
private fun HabitRow(
    item: HabitItem,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = item.todayChecked
    val habit = item.habit
    val timeText = if (habit.reminderMin >= 0) formatMinute(habit.reminderMin) else null
    val typeTint = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCheck)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型图标：习惯用 Repeat，与计划 TabRow 一致用于一眼区分任务/习惯。
        Icon(
            imageVector = Icons.Filled.Repeat,
            contentDescription = null,
            tint = typeTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        // 打卡勾选圆圈。
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = stringResource(if (checked) R.string.habits_checked_desc else R.string.habits_unchecked_desc),
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        if (habit.emoji.isNotEmpty()) {
            Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = habit.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = if (checked) Modifier.alpha(0.6f) else Modifier,
        )
        if (timeText != null) {
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 今日页主列表的行类型抽象：任务与习惯共用同一条 LazyColumn、按 sortKey（提醒分钟数）混排。
 * Task 携带 activeIndex 用于与键盘焦点的 active 段序号对账，保持 V2.54 的焦点行为。
 * sortKey 为 null 时（任务无时间、习惯未设提醒）走 Int.MIN_VALUE，固定排到最上。
 */
private sealed class TodayLine {
    abstract val sortKey: Int?
    abstract val key: String

    data class Task(
        val item: TodayItem,
        val group: TaskGroup?,
        val activeIndex: Int,
    ) : TodayLine() {
        override val sortKey: Int? = item.dueMinute
        override val key: String = "task_" + item.instanceId
    }

    data class Habit(val habit: HabitItem) : TodayLine() {
        override val sortKey: Int? = if (habit.habit.reminderMin >= 0) habit.habit.reminderMin else null
        override val key: String = "habit_" + habit.habit.id
    }
}
