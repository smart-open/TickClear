package com.tickclear.app.ui.tools

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(
    vm: CountdownViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pickedEpoch by remember { mutableStateOf<Long?>(null) }

    val today = LocalDate.now()
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_countdown_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.countdown_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        val picker = DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                pickedEpoch = LocalDate.of(y, m + 1, d)
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            },
                            today.year,
                            today.monthValue - 1,
                            today.dayOfMonth,
                        )
                        picker.show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (pickedEpoch != null) {
                            dateFmt.format(Date(pickedEpoch!!))
                        } else {
                            stringResource(R.string.countdown_pick_date)
                        },
                    )
                }
                Button(
                    onClick = {
                        val epoch = pickedEpoch
                        if (name.isNotBlank() && epoch != null) {
                            vm.add(name.trim(), epoch)
                            name = ""
                            pickedEpoch = null
                        }
                    },
                    enabled = name.isNotBlank() && pickedEpoch != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.countdown_add))
                }
            }

            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.countdown_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(events, key = { "${it.name}|${it.targetEpochMs}" }) { event ->
                        CountdownItem(event = event, onDelete = { vm.remove(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownItem(event: CountdownEvent, onDelete: () -> Unit) {
    val days = ((event.targetEpochMs - System.currentTimeMillis()) / 86_400_000L).toInt()
    val label = when {
        days > 0 -> stringResource(R.string.countdown_days_left, days)
        days == 0 -> stringResource(R.string.countdown_today)
        else -> stringResource(R.string.countdown_passed, -days)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.countdown_delete),
                modifier = Modifier.clickable(onClick = onDelete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
