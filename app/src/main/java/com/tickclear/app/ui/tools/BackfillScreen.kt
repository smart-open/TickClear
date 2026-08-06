package com.tickclear.app.ui.tools

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 打卡补录工具（V2.9++）：为习惯或每日记录手动补打 / 取消过往任意日期的打卡，
 * 补全缺失的打卡记录。底层仓库本就支持任意日期，本页开放显式入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillScreen(
    vm: BackfillViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val habits by vm.habits.collectAsStateWithLifecycle(initialValue = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var selectedHabitId by remember { mutableStateOf<String?>(null) }
    var habitDate by remember { mutableStateOf(today) }
    var dailyDate by remember { mutableStateOf(today) }
    var habitStatus by remember { mutableStateOf<Boolean?>(null) }
    var dailyStatus by remember { mutableStateOf<Boolean?>(null) }

    val selectedHabit = habits.firstOrNull { it.id == selectedHabitId }

    LaunchedEffect(selectedHabitId, habitDate) {
        habitStatus = selectedHabitId?.let { vm.habitChecked(it, habitDate) }
    }
    LaunchedEffect(dailyDate) {
        dailyStatus = vm.dailyChecked(dailyDate)
    }

    fun showDatePicker(initial: String, onPicked: (String) -> Unit) {
        val parts = initial.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return
        val dialog = DatePickerDialog(
            context,
            { _: DatePicker, y, m, d ->
                onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            parts[0], parts[1] - 1, parts[2],
        )
        dialog.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_backfill_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.backfill_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.backfill_habit)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.backfill_daily)) },
                )
            }

            if (tab == 0) {
                // 习惯打卡补录
                if (habits.isEmpty()) {
                    Text(
                        stringResource(R.string.backfill_no_habit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedHabit?.let { "${it.emoji} ${it.title}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.backfill_select_habit)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            habits.filter { !it.archived }.forEach { habit ->
                                DropdownMenuItem(
                                    text = { Text("${habit.emoji} ${habit.title}") },
                                    onClick = {
                                        selectedHabitId = habit.id
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = { showDatePicker(habitDate) { habitDate = it } }) {
                        Text(stringResource(R.string.backfill_pick_date))
                    }
                    Text(
                        stringResource(R.string.backfill_date, habitDate),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (habitStatus == true) stringResource(R.string.backfill_checked)
                        else stringResource(R.string.backfill_unchecked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = {
                            val id = selectedHabitId ?: return@Button
                            val checked = habitStatus ?: false
                            scope.launch {
                                vm.setHabitChecked(id, habitDate, !checked)
                                habitStatus = vm.habitChecked(id, habitDate)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (habitStatus == true) stringResource(R.string.backfill_action_uncheck)
                            else stringResource(R.string.backfill_action_check),
                        )
                    }
                }
            } else {
                // 每日打卡补录
                OutlinedButton(onClick = { showDatePicker(dailyDate) { dailyDate = it } }) {
                    Text(stringResource(R.string.backfill_pick_date))
                }
                Text(
                    stringResource(R.string.backfill_date, dailyDate),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (dailyStatus == true) stringResource(R.string.backfill_checked)
                    else stringResource(R.string.backfill_unchecked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = {
                        val checked = dailyStatus ?: false
                        scope.launch {
                            vm.setDailyChecked(dailyDate, !checked)
                            dailyStatus = vm.dailyChecked(dailyDate)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (dailyStatus == true) stringResource(R.string.backfill_action_uncheck)
                        else stringResource(R.string.backfill_action_check),
                    )
                }
            }
        }
    }
}
