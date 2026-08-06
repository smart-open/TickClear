package com.tickclear.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.tickclear.app.ui.theme.Spacing

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
                // 自绘半高 36dp + 靠左的 Tab：M3 TabRow 的 Tab 内部被
                // LocalMinimumInteractiveComponentEnforcement 强制 48dp 最小高度，靠 modifier 减不掉，
                // 故放弃 M3 TabRow；自绘 Row + PlanTab 同时满足"高度减半"与"靠左"两点要求。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlanTab(
                        icon = Icons.Filled.CheckBox,
                        label = stringResource(R.string.tab_tasks),
                        selected = selectedTab == "tasks",
                        onClick = { selectedTab = "tasks" },
                    )
                    Spacer(Modifier.width(Spacing.lg))
                    PlanTab(
                        icon = Icons.Filled.Repeat,
                        label = stringResource(R.string.tab_habits),
                        selected = selectedTab == "habits",
                        onClick = { selectedTab = "habits" },
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

/**
 * 计划 tab 自绘分段（半高 36dp、靠左排列、图标 + 文本、选中加粗 + 主色下划线）。
 * 取代 M3 TabRow：其内部 Tab 被 LocalMinimumInteractiveComponentEnforcement 强制 48dp 最小高度，
 * 靠 modifier 改不掉；同时 M3 TabRow 内的 Tab 强制均分宽度居中，无法靠左。
 * 自绘 Row + PlanTab 同时满足"高度减半"与"靠左"两个要求。
 */
@Composable
private fun PlanTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = color,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
