package com.tickclear.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.tickclear.app.ui.components.showTimedSnackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tickclear.app.ui.components.TaskEditContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.ui.components.TaskEditSheet
import com.tickclear.app.ui.components.formatMinute
import com.tickclear.app.ui.components.groupColor
import com.tickclear.app.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel(),
    isWide: Boolean = false,
    onNavigateToRecycleBin: () -> Unit = {},
    initialOpenEditor: Boolean = false,
    openEditorNonce: String = "",
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    // V2.9：由「新建任务」快捷方式进入时自动弹出新建编辑器。
    // 以 nonce 记录已消费的那一次：同一 nonce 只弹一次（重组/旋转不重复弹），
    // 再次点击快捷方式会带来新的 nonce，因此可以正常二次弹出。
    var consumedEditorNonce by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialOpenEditor, openEditorNonce) {
        if (initialOpenEditor && consumedEditorNonce != openEditorNonce) {
            editingTaskId = null
            showEditor = true
            consumedEditorNonce = openEditorNonce
        }
    }
    var showGroupEditor by rememberSaveable { mutableStateOf(false) }
    var editingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    // 旋转后保持编辑/删除目标：只存 id，实体按 id 从当前列表恢复（Task/TaskGroup 不便直接 saveable）
    val editingTask = state.tasks.find { it.id == editingTaskId }
    val editingGroup = state.groups.find { it.id == editingGroupId }
    val groupToDelete = state.groups.find { it.id == groupToDeleteId }
    // 宽屏主从双栏：选中任务 id（"__new__" 表示新建）；rememberSaveable 使旋转后保持选中
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    // 软删任务撤销提示
    val pending = state.pendingDelete
    val snackbarMsg = pending?.let { stringResource(R.string.tasks_deleted_snack, it.title) } ?: ""
    val restoreLabel = stringResource(R.string.action_restore)
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

    // 任务行点击：宽屏进右侧详情面板，窄屏弹底部编辑弹窗
    val onTaskClick: (Task) -> Unit = if (isWide) {
        { selectedTaskId = it.id }
    } else {
        { editingTaskId = it.id; showEditor = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // V2.8X 顶栏单行标题：Box 强制 48dp 高度下垂直居中。
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRecycleBin) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recycle_bin_title))
                    }
                    IconButton(onClick = { editingGroupId = null; showGroupEditor = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add_group))
                    }
                },
            )
        },
    ) { innerPadding ->
        // V2.8X++：新增 FAB 改为内容盒内 align(BottomEnd)+padding，与「今日」页定位完全一致
        // （避免 Scaffold floatingActionButton 槽与内容区 innerPadding 计法不同导致离底高度不一致）。
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Column(Modifier.fillMaxSize()) {
            // V2.67 标签筛选条：仅在存在标签时显示
            if (state.allTags.isNotEmpty()) {
                TagFilterRow(
                    allTags = state.allTags,
                    selected = state.selectedTags,
                    onToggle = { viewModel.toggleTagFilter(it) },
                    onClear = { viewModel.clearTagFilter() },
                )
            }
            if (isWide) {
                Row(Modifier.fillMaxSize()) {
                    TasksList(
                        state = state,
                        onTaskClick = onTaskClick,
                        onGroupEdit = { editingGroupId = it.id; showGroupEditor = true },
                        onGroupDelete = { groupToDeleteId = it.id },
                        onGroupPause = { viewModel.pauseGroup(it.id) },
                        onGroupResume = { viewModel.resumeGroup(it.id) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = Spacing.sm),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        val selected = state.tasks.find { it.id == selectedTaskId }
                        if (selectedTaskId == null) {
                            Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.tasks_dual_pane_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            key(selectedTaskId) {
                                TaskEditContent(
                                    groups = state.groups,
                                    initial = selected,
                                    onDismiss = { selectedTaskId = null },
                                    onSave = { task -> viewModel.saveTask(task).also { selectedTaskId = null } },
                                    knownTags = state.allTags,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                )
                            }
                        }
                    }
                }
            } else {
                TasksList(
                    state = state,
                    onTaskClick = onTaskClick,
                    onGroupEdit = { editingGroupId = it.id; showGroupEditor = true },
                    onGroupDelete = { groupToDeleteId = it.id },
                    onGroupPause = { viewModel.pauseGroup(it.id) },
                    onGroupResume = { viewModel.resumeGroup(it.id) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        FloatingActionButton(
            onClick = if (isWide) {
                { selectedTaskId = "__new__" }
            } else {
                { editingTaskId = null; showEditor = true }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg).size(40.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
        }
    }
}

    if (showEditor) {
        TaskEditSheet(
            groups = state.groups,
            initial = editingTask,
            onDismiss = { showEditor = false },
            onSave = { viewModel.saveTask(it) },
            knownTags = state.allTags,
        )
    }

    if (showGroupEditor) {
        GroupEditDialog(
            initial = editingGroup,
            onDismiss = { showGroupEditor = false },
            onSave = { viewModel.saveGroup(it) },
        )
    }

    groupToDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { groupToDeleteId = null },
            title = { Text(stringResource(R.string.tasks_delete_group_title)) },
            text = { Text(stringResource(R.string.tasks_delete_group_confirm, g.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroupCascade(g.id)
                    groupToDeleteId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TagFilterRow(
    allTags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (selected.isNotEmpty()) {
            item(key = "__tag_filter_clear__") {
                FilterChip(
                    selected = false,
                    onClick = onClear,
                    label = { Text(stringResource(R.string.tasks_filter_clear)) },
                )
            }
        }
        items(allTags, key = { it }) { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
            )
        }
    }
}

@Composable
private fun TasksList(
    state: TasksUiState,
    onTaskClick: (Task) -> Unit,
    onGroupEdit: (TaskGroup) -> Unit,
    onGroupDelete: (TaskGroup) -> Unit,
    onGroupPause: (TaskGroup) -> Unit,
    onGroupResume: (TaskGroup) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 组内/无分组均按实例生效分钟升序（无时刻置末尾）
    val byTime = compareBy<Task> { it.instanceDueMinute() ?: Int.MAX_VALUE }
    val ungrouped = state.tasks.filter { it.groupId == null }.sortedWith(byTime)
    // 分组：空组跳过；组间按组内最早任务时间升序
    val sortedGroups = state.groups.mapNotNull { group ->
        val tasks = state.tasks.filter { it.groupId == group.id }.sortedWith(byTime)
        if (tasks.isEmpty()) null else group to tasks
    }.sortedBy { (_, tasks) -> tasks.first().instanceDueMinute() ?: Int.MAX_VALUE }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp, top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier,
    ) {
        if (state.tasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

            // 分组：组间已按组内最早时间排序，组内已按时间排序
            for ((group, tasksInGroup) in sortedGroups) {
                // V2.33：组内任务非空且全部处于暂停态时，认为该组已暂停。
                val groupPaused = tasksInGroup.isNotEmpty() &&
                    tasksInGroup.all { TaskStatus.fromCode(it.status) == TaskStatus.PAUSED }
                item(key = "group_header_${group.id}") {
                    GroupHeaderRow(
                        group = group,
                        count = tasksInGroup.size,
                        paused = groupPaused,
                        onEdit = { onGroupEdit(group) },
                        onDelete = { onGroupDelete(group) },
                        onTogglePause = { if (groupPaused) onGroupResume(group) else onGroupPause(group) },
                    )
                }
                items(
                    tasksInGroup,
                    key = { it.id },
                ) { task ->
                    TaskRow(
                        task = task,
                        onEdit = { onTaskClick(task) },
                        onDelete = { onDeleteTask(task.id) },
                    )
                }
            }

        // 无分组任务
        if (ungrouped.isNotEmpty()) {
            item(key = "ungrouped_header") {
                Text(
                    text = stringResource(R.string.tasks_no_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            items(ungrouped, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onEdit = { onTaskClick(task) },
                    onDelete = { onDeleteTask(task.id) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderRow(
    group: TaskGroup,
    count: Int,
    paused: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val gc = groupColor(group.colorKey)
    val groupCd = stringResource(R.string.a11y_group_header, group.name)
    // 分组分隔条：让分组边界更醒目（组色细线）。
    HorizontalDivider(color = gc.copy(alpha = 0.4f))
    // V2.8X 分组行紧凑化：行高约减半（原 ~56dp → ~28dp）。
    // 关键在三点：① 垂直 padding sm→xs；② 去掉原「组名 + 计数」两行 Column 改为单行；
    // ③ IconButton 默认 48dp 触控盒是真正的高度瓶颈，显式压到 28dp。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onEdit() }
            .semantics { contentDescription = groupCd }
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 原最左侧组色竖条已移除（用户反馈冗余）；组色改由图标底色 + 数字圆圈承载。
        Text(
            text = group.icon,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(gc.copy(alpha = 0.15f))
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
        )
        // 组名 + 数字圆圈：圆圈顶对齐贴在组名右上角（角标语义），不再单占一行。
        Row(
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(3.dp))
            // 数字仅是视觉角标，读屏时补读完整语义「N 个任务」。
            val countCd = stringResource(R.string.tasks_group_count, count)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(gc.copy(alpha = 0.22f))
                    .semantics { contentDescription = countCd },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = gc,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onTogglePause, modifier = Modifier.size(28.dp)) {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(if (paused) R.string.tasks_group_resume else R.string.tasks_group_pause),
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val taskCd = stringResource(R.string.a11y_task_item, task.title)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); false } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.a11y_swipe_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        content = {
            val paused = TaskStatus.fromCode(task.status) == TaskStatus.PAUSED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() }
                    .semantics { contentDescription = taskCd }
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (paused) TextDecoration.LineThrough else null,
                    )
                    Row(
                        modifier = Modifier.padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (task.allDay) stringResource(R.string.task_all_day) else formatMinute(task.instanceDueMinute()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (paused) {
                            Text(
                                stringResource(R.string.task_paused),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (task.tags.isNotEmpty()) {
                            Text(
                                text = task.tags.joinToString(" ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    )
}
