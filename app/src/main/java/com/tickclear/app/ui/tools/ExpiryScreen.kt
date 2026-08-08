package com.tickclear.app.ui.tools

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DAYS_BEFORE_OPTIONS = listOf(0, 1, 3, 7)

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

private fun daysLeft(epochDay: Long): Int =
    (epochDay - LocalDate.now().toEpochDay()).toInt()

/**
 * 临近过期色温告警：剩余天数越少越偏红、越多越偏绿（红→琥珀→绿平滑过渡）。
 * 深色主题下使用更亮的同色系，保证文字可读性。
 */
private fun expiryStatusColor(left: Int, dark: Boolean): Color {
    // t: 0 = 已过期(最红), 1 = ≥30 天(绿)
    val t = (left.coerceAtMost(30) / 30f).coerceAtLeast(0f)
    val red = if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F)
    val amber = if (dark) Color(0xFFFFB300) else Color(0xFFF9A825)
    val green = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    return if (t < 0.5f) lerp(red, amber, t / 0.5f) else lerp(amber, green, (t - 0.5f) / 0.5f)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpiryScreen(
    vm: ExpiryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val categories = stringArrayResource(R.array.expiry_category_entries)

    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpiryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ExpiryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_expiry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expiry_add))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.expiry_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(items = items, key = { it.id }) { entity ->
                        ExpiryItemCard(
                            entity = entity,
                            onClick = {
                                editing = entity
                                showDialog = true
                            },
                            onDelete = { deleteTarget = entity },
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        ExpiryEditDialog(
            initial = editing,
            categories = categories.toList(),
            onDismiss = { showDialog = false },
            onSave = { draft ->
                vm.upsert(draft)
                showDialog = false
            },
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.expiry_delete_title)) },
            text = { Text(stringResource(R.string.expiry_delete_confirm, target.title)) },
            confirmButton = {
                Button(onClick = {
                    vm.delete(target)
                    deleteTarget = null
                }) { Text(stringResource(R.string.expiry_delete_confirm_btn)) }
            },
            dismissButton = {
                Button(onClick = { deleteTarget = null }) { Text(stringResource(R.string.expiry_cancel)) }
            },
        )
    }
}

@Composable
private fun ExpiryItemCard(
    entity: ExpiryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val left = daysLeft(entity.expireEpochDay)
    val leftText = if (left <= 0) {
        stringResource(R.string.expiry_expired)
    } else {
        stringResource(R.string.expiry_days_left, left)
    }
    val statusColor = expiryStatusColor(left, isSystemInDarkTheme())
    // 剩余天数进入「提前提醒」窗口（剩 ≤ 提前天数）时高亮标记
    val soon = entity.reminderEnabled && left in 1..entity.reminderDaysBefore

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(text = entity.title, style = MaterialTheme.typography.titleMedium)
                    // 分类标签
                    Tag(
                        text = entity.category,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        fg = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (soon) {
                        Tag(
                            text = stringResource(R.string.expiry_soon),
                            bg = statusColor,
                            fg = Color.White,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            Text(
                text = "${formatDate(entity.expireEpochDay)} · $leftText",
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            // 提醒信息：到期前 N 天 09:00 系统通知推送；未开启则提示
            if (entity.reminderEnabled) {
                val remindDate = formatDate(entity.expireEpochDay - entity.reminderDaysBefore)
                Text(
                    text = stringResource(R.string.expiry_reminder_line, remindDate, "09:00"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.expiry_reminder_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 卡片内小标签（分类 / 即将提醒）。 */
@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpiryEditDialog(
    initial: ExpiryEntity?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExpiryEntity) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: categories.first()) }
    var expireEpochDay by remember { mutableLongStateOf(initial?.expireEpochDay ?: LocalDate.now().plusDays(30).toEpochDay()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var reminderEnabled by remember { mutableStateOf(initial?.reminderEnabled ?: true) }
    var reminderDaysBefore by remember { mutableIntStateOf(initial?.reminderDaysBefore ?: 1) }
    var recurring by remember { mutableStateOf(initial?.recurring ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.expiry_add else R.string.expiry_edit)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.expiry_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.expiry_category_label), style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                        )
                    }
                }

                val dateText = formatDate(expireEpochDay)
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.expiry_date_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val init = LocalDate.ofEpochDay(expireEpochDay)
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> expireEpochDay = LocalDate.of(y, m + 1, d).toEpochDay() },
                                init.year, init.monthValue - 1, init.dayOfMonth,
                            ).show()
                        },
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.expiry_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.expiry_reminder_enable), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }

                if (reminderEnabled) {
                    Text(stringResource(R.string.expiry_reminder_advance), style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        DAYS_BEFORE_OPTIONS.forEach { days ->
                            FilterChip(
                                selected = reminderDaysBefore == days,
                                onClick = { reminderDaysBefore = days },
                                label = { Text(dayBeforeLabel(days)) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.expiry_recurring), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        ExpiryEntity(
                            id = initial?.id ?: 0L,
                            title = title.trim(),
                            category = category,
                            expireEpochDay = expireEpochDay,
                            note = note.trim(),
                            reminderEnabled = reminderEnabled,
                            reminderDaysBefore = reminderDaysBefore,
                            recurring = recurring,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.expiry_save)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.expiry_cancel)) }
        },
    )
}

@Composable
private fun dayBeforeLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.expiry_days_before_0)
    1 -> stringResource(R.string.expiry_days_before_1)
    3 -> stringResource(R.string.expiry_days_before_3)
    7 -> stringResource(R.string.expiry_days_before_7)
    else -> stringResource(R.string.expiry_days_before_1)
}
