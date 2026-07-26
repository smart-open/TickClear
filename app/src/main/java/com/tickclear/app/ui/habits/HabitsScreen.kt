package com.tickclear.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
            TopAppBar(modifier = Modifier.height(48.dp), title = { Text(stringResource(R.string.habits_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                // 底部新建按钮减小约三分之一（56dp → 40dp），保持圆形。
                modifier = Modifier.size(40.dp),
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (habit.emoji.isNotEmpty()) {
                Text(habit.emoji, style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.habits_streak, item.streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.dueToday) {
                    Text(
                        stringResource(R.string.habits_rest_day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onToggle) {
                    Text(
                        if (item.todayChecked) {
                            stringResource(R.string.habits_uncheck)
                        } else {
                            stringResource(R.string.habits_checkin)
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text(stringResource(R.string.habits_emoji_hint)) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.habits_repeat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekLabels.forEachIndexed { idx, label ->
                        val day = idx + 1
                        val isSel = day in selected
                        androidx.compose.material3.FilterChip(
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.remove(day) else selected.add(day)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    )
}
