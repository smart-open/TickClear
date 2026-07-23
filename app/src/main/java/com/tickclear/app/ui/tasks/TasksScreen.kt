package com.tickclear.app.ui.tasks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.unit.dp
import com.tickclear.app.ui.components.TaskEditContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TaskGroupEntity?>(null) }
    var groupToDelete by remember { mutableStateOf<TaskGroupEntity?>(null) }
    // 宽屏主从双栏：选中任务 id（"__new__" 表示新建）；rememberSaveable 使旋转后保持选中
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    // 软删任务撤销提示
    val pending = state.pendingDelete
    val snackbarMsg = pending?.let { stringResource(R.string.tasks_deleted_snack, it.title) } ?: ""
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

    // 任务行点击：宽屏进右侧详情面板，窄屏弹底部编辑弹层
    val onTaskClick: (TaskEntity) -> Unit = if (isWide) {
        { selectedTaskId = it.id }
    } else {
        { editingTask = it; showEditor = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToRecycleBin) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recycle_bin_title))
                    }
                    IconButton(onClick = { editingGroup = null; showGroupEditor = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add_group))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (isWide) {
                    { selectedTaskId = "__new__" }
                } else {
                    { editingTask = null; showEditor = true }
                },
                modifier = Modifier.padding(Spacing.lg),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { innerPadding ->
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                TasksList(
                    state = state,
                    onTaskClick = onTaskClick,
                    onGroupEdit = { editingGroup = it; showGroupEditor = true },
                    onGroupDelete = { groupToDelete = it },
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
                onGroupEdit = { editingGroup = it; showGroupEditor = true },
                onGroupDelete = { groupToDelete = it },
                onDeleteTask = { viewModel.deleteTask(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (showEditor) {
        TaskEditSheet(
            groups = state.groups,
            initial = editingTask,
            onDismiss = { showEditor = false },
            onSave = { viewModel.saveTask(it) },
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
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.tasks_delete_group_title)) },
            text = { Text(stringResource(R.string.tasks_delete_group_confirm, g.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(g.id)
                    groupToDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TasksList(
    state: TasksUiState,
    onTaskClick: (TaskEntity) -> Unit,
    onGroupEdit: (TaskGroupEntity) -> Unit,
    onGroupDelete: (TaskGroupEntity) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ungrouped = state.tasks.filter { it.groupId == null }
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

        // 按组分组
        for (group in state.groups) {
            item(key = "group_header_${group.id}") {
                GroupHeaderRow(
                    group = group,
                    count = state.tasks.count { it.groupId == group.id },
                    onEdit = { onGroupEdit(group) },
                    onDelete = { onGroupDelete(group) },
                )
            }
            items(
                state.tasks.filter { it.groupId == group.id },
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
    group: TaskGroupEntity,
    count: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val gc = groupColor(group.colorKey)
    val groupCd = stringResource(R.string.a11y_group_header, group.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .semantics { contentDescription = groupCd }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.icon,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(gc.copy(alpha = 0.15f))
                .padding(Spacing.sm),
        )
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.tasks_group_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
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
                    }
                }
            }
        },
    )
}
