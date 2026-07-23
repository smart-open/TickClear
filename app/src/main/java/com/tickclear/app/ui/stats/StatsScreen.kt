package com.tickclear.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.domain.usecase.GroupStat
import com.tickclear.app.ui.components.HeatmapCalendar
import com.tickclear.app.ui.components.MedalWall
import com.tickclear.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) },
    ) { padding ->
        StatsContent(
            viewModel = viewModel,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * 统计主体（不含 Scaffold/TopAppBar），可独立注入 ViewModel。
 * 既用于统计 Tab，也用于今日 Tab 宽屏的统计侧栏。
 */
@Composable
fun StatsContent(
    viewModel: StatsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()

    val hasData = state.totalCompleted > 0 || state.byGroup.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
            if (!hasData) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.stats_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // 汇总卡 2x2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        emoji = "📊",
                        value = "${state.todayCompleted}/${state.todayTotal}",
                        label = stringResource(R.string.stats_today_rate),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        emoji = "🔥",
                        value = stringResource(R.string.stats_streak_value, state.streakDays),
                        label = stringResource(R.string.stats_streak),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        emoji = "✅",
                        value = "${state.totalCompleted}",
                        label = stringResource(R.string.stats_total_completed),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        emoji = "📅",
                        value = "${state.checkInDays}",
                        label = stringResource(R.string.stats_checkin_days),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 完成趋势
            SectionTitle(stringResource(R.string.stats_trend))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = period == StatsPeriod.DAY,
                    onClick = { viewModel.setPeriod(StatsPeriod.DAY) },
                    label = { Text(stringResource(R.string.period_day)) },
                )
                FilterChip(
                    selected = period == StatsPeriod.WEEK,
                    onClick = { viewModel.setPeriod(StatsPeriod.WEEK) },
                    label = { Text(stringResource(R.string.period_week)) },
                )
                FilterChip(
                    selected = period == StatsPeriod.MONTH,
                    onClick = { viewModel.setPeriod(StatsPeriod.MONTH) },
                    label = { Text(stringResource(R.string.period_month)) },
                )
            }
            TrendBars(trend, period)

            // 热力图
            SectionTitle(stringResource(R.string.stats_heatmap))
            HeatmapCalendar(completions = state.completions)

            // 分组完成
            SectionTitle(stringResource(R.string.stats_by_group))
            if (state.byGroup.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_group),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.byGroup.forEach { GroupBarRow(it) }
                }
            }

            // 勋章墙
            SectionTitle(stringResource(R.string.stats_medals))
            MedalWall(unlocked = state.unlockedMedals)
        }
    }

@Composable
private fun StatCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TrendBars(data: List<TrendBucket>, period: StatsPeriod, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val max = (data.maxOf { it.count }).coerceAtLeast(1)
    val labelStep = when {
        data.size <= 8 -> 1
        data.size <= 16 -> 2
        else -> 3
    }
    Box(modifier.fillMaxWidth().height(140.dp)) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEachIndexed { i, b ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val h = ((b.count.toFloat() / max) * 104f).dp
                    Box(
                        Modifier
                            .height(h)
                            .widthIn(min = 4.dp, max = 22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    if (i % labelStep == 0) {
                        Spacer(Modifier.height(4.dp))
                        val displayLabel = if (period == StatsPeriod.MONTH) {
                            stringResource(R.string.stats_trend_month, b.label.toIntOrNull() ?: 0)
                        } else b.label
                        Text(displayLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupBarRow(g: GroupStat) {
    val rate = if (g.total > 0) g.completed.toFloat() / g.total else 0f
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(g.groupName, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.stats_group_count, g.completed, g.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { rate },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
