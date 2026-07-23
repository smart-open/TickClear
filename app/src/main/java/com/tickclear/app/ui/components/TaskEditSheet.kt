package com.tickclear.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.ui.components.DropdownField
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val REPEAT_OPTIONS = listOf(
    "NONE" to R.string.repeat_none,
    "DAILY" to R.string.repeat_daily,
    "WEEKLY" to R.string.repeat_weekly,
    "MONTHLY" to R.string.repeat_monthly,
    "INTERVAL" to R.string.repeat_interval,
)
private val LEVEL_OPTIONS = listOf(
    "high" to R.string.reminder_high,
    "mid" to R.string.reminder_mid,
    "low" to R.string.reminder_low,
)
private val OFFSET_OPTIONS = listOf(
    0 to R.string.reminder_offset_on,
    5 to R.string.reminder_offset_5,
    15 to R.string.reminder_offset_15,
    30 to R.string.reminder_offset_30,
)

/**
 * 新建/编辑任务底部弹层（手机/Compact 形态）。
 * 真实表单内容抽为 [TaskEditContent]，宽屏双栏时直接在右侧面板复用，避免重复实现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditSheet(
    groups: List<TaskGroupEntity>,
    initial: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: suspend (TaskEntity) -> List<TaskEntity>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        TaskEditContent(
            groups = groups,
            initial = initial,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}

/**
 * 任务编辑表单内容（与 Modal 解耦，可在底部弹层或宽屏侧栏复用）。
 * - 含时间选择器（非全天）、重复、提醒开关/级别/提前量；
 * - 保存时调用 onSave 返回冲突列表，存在冲突则显示红条但仍可「仍要保存」。
 */
// getLastKnownLocation 调用已由 accompanist fineLocationPermission.status is Granted 运行时守卫，lint 无法识别该守卫，故显式抑制。
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TaskEditContent(
    groups: List<TaskGroupEntity>,
    initial: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: suspend (TaskEntity) -> List<TaskEntity>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var groupId by remember { mutableStateOf(initial?.groupId) }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var startMin by remember { mutableIntStateOf(initial?.scheduledStartMin ?: (9 * 60)) }
    var endMin by remember { mutableIntStateOf(initial?.scheduledEndMin ?: (9 * 60 + 30)) }
    var repeatType by remember { mutableStateOf(initial?.repeatType ?: "NONE") }
    // 自定义间隔（INTERVAL）：数值 + 单位（天/小时）
    var intervalValue by remember { mutableIntStateOf(initial?.repeatIntervalDays ?: initial?.repeatIntervalHours ?: 1) }
    var intervalUnit by remember { mutableStateOf(if (initial?.repeatIntervalHours != null) "HOURS" else "DAYS") }
    var reminderEnabled by remember { mutableStateOf(initial?.reminderEnabled ?: false) }
    var reminderLevel by remember { mutableStateOf(initial?.reminderLevel ?: "mid") }
    var reminderOffset by remember { mutableIntStateOf(initial?.reminderOffsetMin ?: 0) }
    var locationEnabled by remember { mutableStateOf(initial?.geoLat != null && initial?.geoLng != null && initial?.geoRadius != null) }
    var geoLatText by remember { mutableStateOf(initial?.geoLat?.toString() ?: "") }
    var geoLngText by remember { mutableStateOf(initial?.geoLng?.toString() ?: "") }
    var geoRadiusText by remember { mutableStateOf((initial?.geoRadius ?: 100).toString()) }
    val fineLocationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    // 后台定位：Android 10+（API 29+）地理围栏后台触发需「始终允许」；低版本随 fine 隐式授予。
    val backgroundLocationPermission = rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val context = LocalContext.current
    var showConflict by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val defaultTaskTitle = stringResource(R.string.task_default_title)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(if (initial == null) R.string.task_edit_new else R.string.task_edit_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.task_title_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.task_note_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )

        // 任务组
        DropdownField(
            label = stringResource(R.string.task_group),
            options = listOf(null to stringResource(R.string.group_none)) + groups.map { it.id to it.name },
            selectedKey = groupId,
            onSelected = { newGid ->
                groupId = newGid
                // 组级定时继承：新建任务选入某组时，默认继承该组的重复频率与锚点时间。
                if (initial == null && newGid != null) {
                    val g = groups.find { it.id == newGid }
                    if (g != null && g.repeatType != "NONE") {
                        repeatType = g.repeatType
                        g.repeatAnchorMin?.let { anchor ->
                            startMin = anchor
                            endMin = (anchor + 30).coerceAtMost(23 * 60 + 59)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )

        // 全天
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.task_all_day), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = allDay, onCheckedChange = { allDay = it })
        }

        if (!allDay) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TimeButton(
                    label = stringResource(R.string.task_start_time),
                    min = startMin,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                )
                TimeButton(
                    label = stringResource(R.string.task_end_time),
                    min = endMin,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 重复
        DropdownField(
            label = stringResource(R.string.task_repeat),
            options = REPEAT_OPTIONS.map { (k, r) -> k to stringResource(r) },
            selectedKey = repeatType,
            onSelected = { repeatType = it },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        if (repeatType == "INTERVAL") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = intervalValue.toString(),
                    onValueChange = { intervalValue = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                    label = { Text(stringResource(R.string.repeat_interval_value)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    label = stringResource(R.string.repeat_interval_unit),
                    options = listOf("DAYS" to stringResource(R.string.repeat_interval_unit_days), "HOURS" to stringResource(R.string.repeat_interval_unit_hours)),
                    selectedKey = intervalUnit,
                    onSelected = { intervalUnit = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 提醒
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.task_reminder), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
        }
        if (reminderEnabled) {
            DropdownField(
                label = stringResource(R.string.reminder_level),
                options = LEVEL_OPTIONS.map { (k, r) -> k to stringResource(r) },
                selectedKey = reminderLevel,
                onSelected = { reminderLevel = it },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            DropdownField(
                label = stringResource(R.string.reminder_offset),
                options = OFFSET_OPTIONS.map { (k, r) -> k to stringResource(r) },
                selectedKey = reminderOffset,
                onSelected = { reminderOffset = it },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }

        if (showConflict) {
            Text(
                text = stringResource(R.string.task_conflict_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        // 位置提醒（地理围栏）：开启后填入经纬度与半径，进入范围触发提醒。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.task_location), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = locationEnabled, onCheckedChange = { locationEnabled = it })
        }
        if (locationEnabled) {
            OutlinedTextField(
                value = geoLatText,
                onValueChange = { geoLatText = it },
                label = { Text(stringResource(R.string.task_location_lat)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            OutlinedTextField(
                value = geoLngText,
                onValueChange = { geoLngText = it },
                label = { Text(stringResource(R.string.task_location_lng)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            OutlinedTextField(
                value = geoRadiusText,
                onValueChange = { geoRadiusText = it },
                label = { Text(stringResource(R.string.task_location_radius)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            Button(
                onClick = {
                    if (fineLocationPermission.status is PermissionStatus.Granted) {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        if (loc != null) {
                            geoLatText = loc.latitude.toString()
                            geoLngText = loc.longitude.toString()
                        }
                    } else {
                        fineLocationPermission.launchPermissionRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text(stringResource(R.string.task_location_use_current), modifier = Modifier.padding(start = 4.dp))
            }
            if (fineLocationPermission.status !is com.google.accompanist.permissions.PermissionStatus.Granted) {
                Text(
                    text = stringResource(R.string.task_location_perm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            } else if (backgroundLocationPermission.status !is PermissionStatus.Granted) {
                // 前台定位已授予但缺后台定位：地理围栏退到后台会静默失效，引导用户授予「始终允许」。
                Text(
                    text = stringResource(R.string.task_location_bg_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                TextButton(
                    onClick = { backgroundLocationPermission.launchPermissionRequest() },
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    Text(stringResource(R.string.task_location_bg_grant))
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                val geoLat = if (locationEnabled) geoLatText.toDoubleOrNull() else null
                val geoLng = if (locationEnabled) geoLngText.toDoubleOrNull() else null
                val geoRadius = if (locationEnabled) geoRadiusText.toIntOrNull() ?: 100 else null
                val task = buildTask(
                    initial = initial,
                    title = title,
                    defaultTitle = defaultTaskTitle,
                    notes = notes,
                    groupId = groupId,
                    allDay = allDay,
                    startMin = startMin,
                    endMin = endMin,
                    repeatType = repeatType,
                    intervalValue = intervalValue,
                    intervalUnit = intervalUnit,
                    reminderEnabled = reminderEnabled,
                    reminderLevel = reminderLevel,
                    reminderOffset = reminderOffset,
                    geoLat = geoLat,
                    geoLng = geoLng,
                    geoRadius = geoRadius,
                )
                    val conflicts = onSave(task)
                    if (conflicts.isEmpty()) onDismiss() else showConflict = true
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMin = startMin,
            onConfirm = { startMin = it; showStartPicker = false },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMin = endMin,
            onConfirm = { endMin = it; showEndPicker = false },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun TimeButton(label: String, min: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clickable { onClick() }) {
        OutlinedTextField(
            value = formatMinute(min),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
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

private fun buildTask(
    initial: TaskEntity?,
    title: String,
    defaultTitle: String,
    notes: String,
    groupId: String?,
    allDay: Boolean,
    startMin: Int,
    endMin: Int,
    repeatType: String,
    intervalValue: Int = 1,
    intervalUnit: String = "DAYS",
    reminderEnabled: Boolean,
    reminderLevel: String,
    reminderOffset: Int,
    geoLat: Double?,
    geoLng: Double?,
    geoRadius: Int?,
): TaskEntity {
    val today = LocalDate.now()
    val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val (scheduledDate: String?, repeatOut: String, weekdays: String?, monthDay: Int?) = when (repeatType) {
        "DAILY" -> Tuple(null, "DAILY", null, null)
        "WEEKLY" -> Tuple(null, "WEEKLY", today.dayOfWeek.value.toString(), null)
        "MONTHLY" -> Tuple(null, "MONTHLY", null, today.dayOfMonth)
        "INTERVAL" -> Tuple(null, "INTERVAL", null, null)
        else -> Tuple(dateStr, "NONE", null, null)
    }

    val isIntervalHours = repeatType == "INTERVAL" && intervalUnit == "HOURS"
    val isIntervalDays = repeatType == "INTERVAL" && intervalUnit == "DAYS"

    return TaskEntity(
        id = initial?.id ?: UUID.randomUUID().toString(),
        groupId = groupId,
        title = title.trim().ifEmpty { defaultTitle },
        notes = notes,
        status = initial?.status ?: 0,
        scheduledStartMin = if (allDay) null else startMin,
        scheduledEndMin = if (allDay) null else endMin,
        allDay = allDay,
        scheduledDate = scheduledDate,
        repeatType = repeatOut,
        repeatIntervalDays = if (isIntervalDays) intervalValue.coerceAtLeast(1) else null,
        repeatIntervalHours = if (isIntervalHours) intervalValue.coerceAtLeast(1) else null,
        repeatWeekdays = weekdays,
        repeatMonthDay = monthDay,
        repeatAnchorMin = if (allDay) null else startMin,
        repeatAnchorDate = if (repeatType == "INTERVAL") dateStr else null,
        reminderEnabled = reminderEnabled,
        reminderLevel = reminderLevel,
        reminderOffsetMin = if (reminderEnabled) reminderOffset else null,
        source = initial?.source ?: "manual",
        geoLat = if (geoLat != null && geoLng != null) geoLat else null,
        geoLng = if (geoLat != null && geoLng != null) geoLng else null,
        geoRadius = if (geoLat != null && geoLng != null) geoRadius else null,
        createdAt = initial?.createdAt ?: System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = initial?.completedAt,
        deletedAt = initial?.deletedAt,
    )
}

/** 轻量四元组，仅用于 buildTask 内重复规则拆解。 */
private data class Tuple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

