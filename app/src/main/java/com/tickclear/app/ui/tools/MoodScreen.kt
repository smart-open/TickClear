package com.tickclear.app.ui.tools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate

private data class MoodDef(val code: Int, val emoji: String, val labelRes: Int)

private val MOODS = listOf(
    MoodDef(1, "\uD83D\uDE0A", R.string.mood_1), // 开心
    MoodDef(2, "\uD83D\uDE42", R.string.mood_2), // 平静
    MoodDef(3, "\uD83D\uDE2A", R.string.mood_3), // 疲惫
    MoodDef(4, "\uD83D\uDE1F", R.string.mood_4), // 焦虑
    MoodDef(5, "\uD83D\uDE22", R.string.mood_5), // 难过
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodScreen(
    vm: MoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    var selectedCode by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    // today 首次到达（或保存后回写）时同步选择，使「今日已打卡」可被编辑/覆盖。
    LaunchedEffect(today) {
        if (today != null) {
            selectedCode = today!!.code
            note = today!!.note
        }
    }

    val context = LocalContext.current
    val todayLabel = stringResource(R.string.mood_today)
    val noteHint = stringResource(R.string.mood_note_hint)
    val saveLabel = stringResource(R.string.mood_save)
    val savedToast = stringResource(R.string.mood_saved_toast)
    val statsLabel = stringResource(R.string.mood_month_stats)
    val noData = stringResource(R.string.mood_no_data)
    val recentLabel = stringResource(R.string.mood_recent)

    // 本月情绪分布
    val now = LocalDate.now()
    val monthKey = now.year * 100 + now.monthValue
    val thisMonth = entries.filter {
        val d = LocalDate.ofEpochDay(it.epochDay)
        d.year * 100 + d.monthValue == monthKey
    }
    val counts = MOODS.associate { m -> m.code to thisMonth.count { e -> e.code == m.code } }
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val recent = entries.takeLast(7).reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_mood_title)) },
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
            SimHintCard(stringResource(R.string.tools_mood_hint))
            Text(todayLabel, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MOODS.forEach { m ->
                    FilterChip(
                        selected = selectedCode == m.code,
                        onClick = { selectedCode = m.code },
                        label = { Text("${m.emoji} " + stringResource(m.labelRes)) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(noteHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val code = selectedCode ?: return@Button
                    vm.saveToday(code, note)
                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                },
                enabled = selectedCode != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(saveLabel) }

            Spacer(Modifier.height(Spacing.sm))
            Text(statsLabel, style = MaterialTheme.typography.titleSmall)
            if (thisMonth.isEmpty()) {
                Text(noData, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MOODS.forEach { m ->
                    val c = counts[m.code] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("${m.emoji} " + stringResource(m.labelRes), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { c / maxCount.toFloat() },
                            modifier = Modifier.weight(1f),
                        )
                        Text("$c", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(recentLabel, style = MaterialTheme.typography.titleSmall)
            recent.forEach { e ->
                val d = LocalDate.ofEpochDay(e.epochDay)
                val mood = MOODS.firstOrNull { it.code == e.code }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("${d.monthValue}.${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
                        val moodLabel = if (mood != null) "${mood.emoji} " + stringResource(mood.labelRes) else "$e.code"
                        Text(
                            moodLabel,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (e.note.isNotBlank()) {
                            Text(
                                e.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
