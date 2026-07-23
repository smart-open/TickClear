package com.tickclear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale

/** 任务组配色（与 D.7 colorTertiary 对应）。 */
val GROUP_COLORS: Map<String, Color> = mapOf(
    "blue" to Color(0xFF2F6BFF),
    "mint" to Color(0xFF21C19B),
    "violet" to Color(0xFF7C5CFF),
    "amber" to Color(0xFFF5A623),
    "rose" to Color(0xFFE5484D),
    "sky" to Color(0xFF3BA7F5),
)

@Composable
fun groupColor(key: String?): Color =
    GROUP_COLORS[key] ?: MaterialTheme.colorScheme.primary

fun formatMinute(min: Int?): String =
    if (min == null) "—" else String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60)

/**
 * 今日任务行：右滑完成、左滑软删（带撤销由 Screen 负责）。
 * confirmValueChange 返回 false，使滑动后回弹（真实删除/完成由数据库驱动列表更新）。
 */
@Composable
fun TaskItem(
    item: com.tickclear.app.domain.usecase.TodayItem,
    group: TaskGroup?,
    isConflict: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    isFocused: Boolean = false,
) {
    val task = item.task
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val isComplete = dir == SwipeToDismissBoxValue.StartToEnd
            val bg = if (isComplete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            val fg = if (isComplete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = if (isComplete) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Filled.Delete,
                    contentDescription = stringResource(
                        if (isComplete) R.string.a11y_swipe_complete else R.string.a11y_swipe_delete,
                    ),
                    tint = fg,
                )
            }
        },
        content = {
            TaskCardContent(
                task = task,
                group = group,
                done = item.done,
                isConflict = isConflict,
                onComplete = onComplete,
                onEdit = onEdit,
                isFocused = isFocused,
            )
        },
    )
}

@Composable
private fun TaskCardContent(
    task: Task,
    group: TaskGroup?,
    done: Boolean,
    isConflict: Boolean,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    isFocused: Boolean = false,
) {
    val timeText = if (task.allDay) stringResource(R.string.task_all_day) else formatMinute(task.instanceDueMinute())
    val taskItemCd = stringResource(R.string.a11y_task_item, task.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable { onEdit() }
            .semantics { contentDescription = taskItemCd }
            .padding(vertical = Spacing.sm, horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onComplete() },
        )
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group != null) {
                    val gc = groupColor(group.colorKey)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(gc.copy(alpha = 0.15f))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = group.icon, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = gc,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
        if (isConflict) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.a11y_time_conflict),
                    tint = MaterialTheme.colorScheme.error,
                )
        }
    }
}
