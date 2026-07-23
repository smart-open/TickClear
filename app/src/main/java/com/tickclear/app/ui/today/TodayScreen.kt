package com.tickclear.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                        onClick = { showClearConfirm = true },
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
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.today_clear_all_title)) },
            text = { Text(stringResource(R.string.today_clear_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAll()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = clearedSnack,
                            duration = SnackbarDuration.Short,
                        )
                    }
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
@OptIn(ExperimentalMaterial3Api::class)
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
    var focusedIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    DisposableEffect(view, shortcutsEnabled) {
        val listener = View.OnKeyListener { _, keyCode, event ->
            if (!shortcutsEnabled || event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            val count = state.items.size
            if (count == 0) return@OnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val next = (focusedIndex + 1).coerceAtMost(count - 1)
                    focusedIndex = next
                    scope.launch { listState.scrollToItem(next) }
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val prev = (focusedIndex - 1).coerceAtLeast(0)
                    focusedIndex = prev
                    scope.launch { listState.scrollToItem(prev) }
                    true
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> {
                    // 完成当前聚焦项；若已完成的则顺延到首个未完成项
                    val target = state.items.getOrNull(focusedIndex)?.takeIf { !it.done }
                        ?: state.items.firstOrNull { !it.done }
                    if (target != null) onComplete(target)
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
                    items(state.items, key = { it.instanceId }) { item ->
                        TaskItem(
                            item = item,
                            group = state.groups[item.task.groupId],
                            isConflict = state.conflictIds.contains(item.instanceId),
                            onComplete = { onComplete(item) },
                            onDelete = { onDelete(item) },
                            onEdit = { onEdit(item) },
                            isFocused = state.items.indexOf(item) == focusedIndex,
                        )
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
