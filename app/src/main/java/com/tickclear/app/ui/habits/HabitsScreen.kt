package com.tickclear.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.ui.theme.Spacing
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel = hiltViewModel(),
    isWide: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HabitItem?>(null) }

    Scaffold(
        topBar = {
            // V2.8X 顶栏单行标题：用 Box 强制 48dp 高度下垂直居中显示，
            // 避免「设置/习惯/统计/任务」等 Tab 标题贴顶。
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(stringResource(R.string.habits_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                // 底部新建按钮减小约三分之一（56dp → 40dp），保持圆形；与今日/任务页对齐底部间距。
                modifier = Modifier.padding(Spacing.lg).size(40.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.habits_add))
            }
        },
    ) { padding ->
        if (uiState.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.habits_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.items, key = { it.habit.id }) { item ->
                    HabitCard(
                        item = item,
                        onToggle = { viewModel.toggleToday(item.habit.id) },
                        onDelete = { pendingDelete = item },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddHabitDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, emoji, repeatDays ->
                viewModel.createHabit(title, emoji, repeatDays)
                showAdd = false
            },
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.habits_delete_title)) },
            text = { Text(stringResource(R.string.habits_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    val habit = pendingDelete ?: return@TextButton
                    viewModel.deleteHabit(habit.habit.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HabitCard(
    item: HabitItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val habit = item.habit
    // V2.8X：卡片高度减少约 1/3（80→48dp），内部 icon + 文字 + 操作行 单行展示 + 居中对齐。
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (habit.emoji.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    habit.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                val streakText = stringResource(R.string.habits_streak, item.streak)
                val subText = if (!item.dueToday) {
                    "$streakText · ${stringResource(R.string.habits_rest_day)}"
                } else {
                    streakText
                }
                Text(
                    subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = onToggle,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    if (item.todayChecked) {
                        stringResource(R.string.habits_uncheck)
                    } else {
                        stringResource(R.string.habits_checkin)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * V2.8X：内置 36 个常用 emoji，覆盖健康/学习/工作/情绪/家庭等高频习惯主题。
 * 点击即替换当前 emoji 文本框内容；不再依赖系统键盘手输。
 */
private val HABIT_EMOJIS = listOf(
    "💧", "🏃", "📚", "🧘", "🥗", "🍎", "☕", "💤",
    "🦷", "💪", "🚶", "🧠", "✍️", "🎨", "🎵", "📝",
    "🧹", "🌱", "🐶", "🧴", "💊", "🍵", "🥛", "🥦",
    "⏰", "🛏️", "🪥", "🧺", "📅", "💻", "📖", "🧗",
    "🎯", "🚴", "🏊", "🧶",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, emoji: String, repeatDays: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    val weekLabels = listOf(
        stringResource(R.string.habits_week_mon),
        stringResource(R.string.habits_week_tue),
        stringResource(R.string.habits_week_wed),
        stringResource(R.string.habits_week_thu),
        stringResource(R.string.habits_week_fri),
        stringResource(R.string.habits_week_sat),
        stringResource(R.string.habits_week_sun),
    )
    val selected = remember { mutableStateListOf(1, 2, 3, 4, 5, 6, 7) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title, emoji, selected.joinToString(",")) },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.habits_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.habits_name_hint)) },
                    singleLine = true,
                )
                // V2.8X：emoji 选择 —— 文本框展示当前选择，下方 36 颗 emoji 候选网格可点选。
                Text(
                    stringResource(R.string.habits_emoji_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    if (emoji.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { emoji = "" }) { Text("×") }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HABIT_EMOJIS.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e, fontSize = 18.sp)
                        }
                    }
                }
                // V2.8X：星期选择改用 LazyRow —— 单行 7 个 chip 必溢出，FlowRow 在窄屏也不易选，
                // 横向滑动既能完整显示又避免被裁剪。
                Text(
                    stringResource(R.string.habits_repeat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(weekLabels.size) { idx ->
                        val day = idx + 1
                        val isSel = day in selected
                        androidx.compose.material3.FilterChip(
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.remove(day) else selected.add(day)
                            },
                            label = { Text(weekLabels[idx]) },
                        )
                    }
                }
            }
        },
    )
}
