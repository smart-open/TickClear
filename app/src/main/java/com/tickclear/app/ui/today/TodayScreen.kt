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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.tickclear.app.domain.usecase.TodayItem
import com.tickclear.app.domain.usecase.TodayListPrefs
import com.tickclear.app.ui.components.ConflictBanner
import com.tickclear.app.ui.components.ProgressRing
import com.tickclear.app.ui.components.TaskEditSheet
import com.tickclear.app.ui.components.TaskItem
import com.tickclear.app.ui.stats.StatsContent
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    isWide: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            snackbarHostState.showSnackbar(message = clearedSnack, duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(pending?.id) {
        if (pending != null) {
            val result = snackbarHostState.showSnackbar(
                message = snackbarMsg,
                actionLabel = restoreLabel,
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                SnackbarResult.Dismissed -> viewModel.clearPending()
            }
        }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, style = MaterialTheme.typography.titleLarge)
                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        size = 44.dp,
                        stroke = 5.dp,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToStats)
                            .semantics { role = Role.Button; contentDescription = ringDesc },
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
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
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
    shortcutsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // V2.10 键盘快捷键：在 Compose 宿主 View 上挂 OnKeyListener 捕获 ↑↓ 选择 / 空格·回车完成 / N 新建。
    // 采用 View 级监听而非 Modifier.focusable，规避本环境对 focusable 符号的解析差异，且无需强制焦点。
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty()) {
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
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = state.encouragement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                        if (shortcutsEnabled) {
                            Text(
                                text = stringResource(R.string.today_kbd_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }

                    // V2.54：以 active 段本地序号 index 判断高亮，与键盘焦点一致；done 段不可键盘聚焦。
                    itemsIndexed(activeItems, key = { _, item -> item.instanceId }) { index, item ->
                        // V2.21 列表项进入/重排动画
                        Box(modifier = Modifier.animateItem()) {
                            TaskItem(
                                item = item,
                                group = state.groups[item.task.groupId],
                                isConflict = state.conflictIds.contains(item.instanceId),
                                onComplete = { onComplete(item) },
                                onDelete = { onDelete(item) },
                                onEdit = { onEdit(item) },
                                isFocused = index == focusedIndex,
                            )
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
                            items(doneItems, key = { it.instanceId }) { item ->
                                Box(modifier = Modifier.animateItem()) {
                                    TaskItem(
                                        item = item,
                                        group = state.groups[item.task.groupId],
                                        isConflict = state.conflictIds.contains(item.instanceId),
                                        onComplete = { onComplete(item) },
                                        onDelete = { onDelete(item) },
                                        onEdit = { onEdit(item) },
                                        isFocused = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
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
