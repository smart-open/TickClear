package com.tickclear.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.ui.theme.Spacing
import com.tickclear.app.ui.theme.Warning
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
 * 今日任务行：右滑切换完成态、左滑软删（带撤销由 Screen 负责）。
 * confirmValueChange 返回 false，使滑动后回弹（真实删除/完成由数据库驱动列表更新）。
 * [onComplete] 为「切换」语义：未完成→完成，已完成→恢复未完成（与习惯打卡一致），
 * 故右滑背景的图标/文案随 [com.tickclear.app.domain.usecase.TodayItem.done] 切换，避免对已完成项仍提示「右滑完成」。
 * [index] 用于隔行变色：偶数行浅底色增强视觉分隔，奇数行纯 surface。
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
    index: Int = 0,
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
            // Settled（未滑动）时用主题 surface，避免静止状态透出 errorContainer 底色
            // 导致每行背景与主题不匹配（问题3）。
            val bg = when (dir) {
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
            val fg = if (isComplete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            val swipeIcon = when {
                !isComplete -> Icons.Filled.Delete
                item.done -> Icons.AutoMirrored.Filled.Undo
                else -> Icons.Filled.CheckCircle
            }
            val swipeCdRes = when {
                !isComplete -> R.string.a11y_swipe_delete
                item.done -> R.string.a11y_swipe_uncomplete
                else -> R.string.a11y_swipe_complete
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = if (isComplete) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                // 仅在滑动过程中出现图标：静止态（Settled）不绘制，避免删除图标从半透明行背景后透出、
                // 与行尾编辑图标视觉叠加（用户反馈「修改和删除图标叠加」）。左滑删除的交互本身由
                // confirmValueChange 的 EndToStart 分支承载，不受此处图标显隐影响。
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = swipeIcon,
                        contentDescription = stringResource(swipeCdRes),
                        tint = fg,
                    )
                }
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
                index = index,
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
    index: Int = 0,
) {
    val timeText = if (task.allDay) stringResource(R.string.task_all_day) else formatMinute(task.instanceDueMinute())
    // 今日行不再整行触发编辑，语义描述去掉「点击编辑」（编辑由行尾图标按钮自带描述）。
    val taskItemCd = stringResource(R.string.a11y_task_row, task.title)
    // V2.21 完成任务微动效：勾选框轻微回弹放大，内容随完成淡出。
    val checkScale by animateFloatAsState(
        targetValue = if (done) 1.12f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.55f),
        label = "checkScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (done) 0.6f else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.7f),
        label = "contentAlpha",
    )
    // 隔行浅底色：偶数行用 surfaceContainer 配 0.55 alpha，奇数行用 surface 略微透明，
    // 强化行间分隔；旧版用 surfaceVariant 0.1f 在浅色主题下几乎不可见。
    val rowBackground = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    // 焦点边框：仅在 focusedIndex 显式 >= 0 时绘制对应行；focusedIndex 初始为 -1 时全列表无边框。
    val drawFocus = isFocused && index >= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .then(
                if (drawFocus) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            // 整行不再吞点击：编辑只由行尾的编辑图标触发，避免「想勾选/想看详情却弹出编辑页」的误触。
            .semantics { contentDescription = taskItemCd }
            .padding(vertical = Spacing.xs, horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onComplete() },
            modifier = Modifier.scale(checkScale),
        )
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm).alpha(contentAlpha)) {
            // 任务类型图标（与计划 TabRow 一致），用于主列表里一眼区分任务/习惯。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckBox,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.padding(top = 0.dp),
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
                    // 浅黄色警告图标（与今日冲突条配色一致）。
                    tint = Warning,
                    modifier = Modifier.size(18.dp),
                )
        }
        // 编辑入口：显式图标按钮（36dp 触控区不撑高行高，Checkbox 自身已占 48dp 高）。
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
