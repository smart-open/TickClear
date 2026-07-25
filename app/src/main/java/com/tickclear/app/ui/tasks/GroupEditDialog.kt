package com.tickclear.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.ui.components.DropdownField
import com.tickclear.app.ui.components.formatMinute
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

private val GROUP_REPEAT_OPTIONS = listOf(
    "NONE" to R.string.repeat_none,
    "DAILY" to R.string.repeat_daily,
    "WEEKLY" to R.string.repeat_weekly,
    "MONTHLY" to R.string.repeat_monthly,
)

private val GROUP_COLOR_OPTIONS = listOf(
    "blue" to R.string.group_color_blue,
    "mint" to R.string.group_color_mint,
    "violet" to R.string.group_color_violet,
    "amber" to R.string.group_color_amber,
    "rose" to R.string.group_color_rose,
    "sky" to R.string.group_color_sky,
)

private val GROUP_ICON_PRESETS = listOf("📁", "💼", "🏠", "🏃", "📚", "💊", "🍎", "🌙")

/**
 * 新建/编辑任务组底部弹层：组名称、图标 emoji、颜色。
 * 保存时回调 onSave（由 ViewModel 决定新增或更新）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditDialog(
    initial: TaskGroup?,
    onDismiss: () -> Unit,
    onSave: suspend (TaskGroup) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "📁") }
    var colorKey by remember { mutableStateOf(initial?.colorKey ?: "blue") }
    var repeatType by remember { mutableStateOf(initial?.repeatType ?: "NONE") }
    var anchorMin by remember { mutableIntStateOf(initial?.repeatAnchorMin ?: 540) }
    var showAnchorPicker by remember { mutableStateOf(false) }
    val defaultGroupName = stringResource(R.string.group_default_name)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(if (initial == null) R.string.group_edit_new else R.string.group_edit_edit),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )

            OutlinedTextField(
                value = icon,
                onValueChange = { icon = it },
                label = { Text(stringResource(R.string.group_icon_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GROUP_ICON_PRESETS.forEach { preset ->
                    AssistChip(onClick = { icon = preset }, label = { Text(preset) })
                }
            }

            DropdownField(
                label = stringResource(R.string.group_color),
                options = GROUP_COLOR_OPTIONS.map { (k, r) -> k to stringResource(r) },
                selectedKey = colorKey,
                onSelected = { colorKey = it },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            // 组级重复频率：组内新建任务默认继承此频率与锚点时间。
            DropdownField(
                label = stringResource(R.string.group_repeat),
                options = GROUP_REPEAT_OPTIONS.map { (k, r) -> k to stringResource(r) },
                selectedKey = repeatType,
                onSelected = { repeatType = it },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
            if (repeatType != "NONE") {
                val anchorDesc = stringResource(
                    R.string.a11y_choose_prefix,
                    stringResource(R.string.group_repeat_anchor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                        .clickable(role = Role.Button, onClick = { showAnchorPicker = true })
                        .semantics { contentDescription = anchorDesc },
                ) {
                    OutlinedTextField(
                        value = formatMinute(anchorMin),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.group_repeat_anchor)) },
                        trailingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        onSave(
                            TaskGroup(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifEmpty { defaultGroupName },
                                icon = icon.ifEmpty { "📁" },
                                colorKey = colorKey,
                                orderIndex = initial?.orderIndex ?: 0,
                                repeatType = repeatType,
                                repeatAnchorMin = anchorMin,
                                status = initial?.status ?: 0,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                deletedAt = initial?.deletedAt,
                            ),
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showAnchorPicker) {
        GroupTimePickerDialog(
            initialMin = anchorMin,
            onConfirm = { anchorMin = it; showAnchorPicker = false },
            onDismiss = { showAnchorPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTimePickerDialog(
    initialMin: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}
