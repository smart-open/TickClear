package com.tickclear.app.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.ui.components.ConfettiOverlay
import com.tickclear.app.ui.habits.HabitsContent
import com.tickclear.app.ui.habits.HabitsViewModel
import com.tickclear.app.ui.tasks.GroupEditDialog
import com.tickclear.app.ui.tasks.TasksContent
import com.tickclear.app.ui.tasks.TasksViewModel

/**
 * 合并 tab「计划」：内部以二级分段（任务 | 习惯）承载原「任务」「习惯」两页的管理能力。
 * 本容器持有唯一 Scaffold / TopAppBar / TabRow / SnackbarHost / 最外层 ConfettiOverlay；
 * 两个 ViewModel 在此创建后传入各自 Content，切换分段零重建成本、数据始终最新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    tasksViewModel: TasksViewModel = hiltViewModel(),
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    isWide: Boolean = false,
    onNavigateToRecycleBin: () -> Unit = {},
    initialTab: String = "tasks",
    initialOpenEditor: Boolean = false,
    openEditorNonce: String = "",
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 分组编辑/删除（含顶栏「新建分组」按钮）状态提升到此处：
    // 顶栏按钮与任务列表内的编辑/删除都需驱动同一对话框。
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    var showGroupEditor by rememberSaveable { mutableStateOf(false) }
    var editingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingGroup = tasksState.groups.find { it.id == editingGroupId }
    val groupToDelete = tasksState.groups.find { it.id == groupToDeleteId }

    // 习惯打卡撒花：ConfettiOverlay 置于最外层 Box，跨分段不被顶栏裁切。
    var confettiTrigger by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            Text(stringResource(R.string.tab_plan), style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        if (selectedTab == "tasks") {
                            IconButton(onClick = onNavigateToRecycleBin) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recycle_bin_title))
                            }
                            IconButton(onClick = { editingGroupId = null; showGroupEditor = true }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add_group))
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                TabRow(selectedTabIndex = if (selectedTab == "habits") 1 else 0) {
                    Tab(
                        selected = selectedTab == "tasks",
                        onClick = { selectedTab = "tasks" },
                        text = { Text(stringResource(R.string.tab_tasks)) },
                    )
                    Tab(
                        selected = selectedTab == "habits",
                        onClick = { selectedTab = "habits" },
                        text = { Text(stringResource(R.string.tab_habits)) },
                    )
                }
                Box(Modifier.fillMaxSize().weight(1f)) {
                    when (selectedTab) {
                        "habits" -> HabitsContent(
                            viewModel = habitsViewModel,
                            onConfetti = { confettiTrigger++ },
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> TasksContent(
                            viewModel = tasksViewModel,
                            snackbarHostState = snackbarHostState,
                            isWide = isWide,
                            initialOpenEditor = initialOpenEditor,
                            openEditorNonce = openEditorNonce,
                            onEditGroup = { editingGroupId = it.id; showGroupEditor = true },
                            onRequestDeleteGroup = { groupToDeleteId = it.id },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        ConfettiOverlay(trigger = confettiTrigger, onFinished = { confettiTrigger = 0 })
    }

    if (showGroupEditor) {
        GroupEditDialog(
            initial = editingGroup,
            onDismiss = { showGroupEditor = false },
            onSave = { tasksViewModel.saveGroup(it) },
        )
    }

    groupToDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { groupToDeleteId = null },
            title = { Text(stringResource(R.string.tasks_delete_group_title)) },
            text = { Text(stringResource(R.string.tasks_delete_group_confirm, g.name)) },
            confirmButton = {
                TextButton(onClick = {
                    tasksViewModel.deleteGroupCascade(g.id)
                    groupToDeleteId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
