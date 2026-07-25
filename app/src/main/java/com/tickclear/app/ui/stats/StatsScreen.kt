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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.domain.model.Medal
import com.tickclear.app.domain.model.MedalProgress
import com.tickclear.app.domain.usecase.GroupStat
import com.tickclear.app.ui.components.HeatmapCalendar
import com.tickclear.app.ui.components.MedalWall
import com.tickclear.app.ui.components.ProgressRing
import com.tickclear.app.ui.components.EmptyStateGuide
import com.tickclear.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    isWide: Boolean = false,
    onGoToday: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) },
    ) { padding ->
        StatsContent(
            viewModel = viewModel,
            isWide = isWide,
            onGoToday = onGoToday,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * 统计主体（不含 Scaffold/TopAppBar），可独立注入 ViewModel。
 * 既用于统计 Tab，也用于今日 Tab 宽屏的统计侧栏。
 * [isWide] 为 true 时（Medium+ 宽屏）概览与明细分双栏，避免大屏横向留白与卡片拉伸。
 */
@Composable
fun StatsContent(
    viewModel: StatsViewModel = hiltViewModel(),
    isWide: Boolean = false,
    onGoToday: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    var selectedMedal by remember { mutableStateOf<Medal?>(null) }

    val hasData = state.totalCompleted > 0 || state.byGroup.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!hasData) {
            EmptyStateGuide(
                icon = "📊",
                title = stringResource(R.string.stats_empty_title),
                message = stringResource(R.string.stats_empty_hint),
                actionLabel = onGoToday?.let { stringResource(R.string.stats_go_today) },
                onAction = onGoToday,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
            )
        } else if (isWide) {
            // V2.19 宽屏：概览与明细分双栏，避免大屏横向留白与卡片被拉伸。
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatsOverviewColumn(state = state, modifier = Modifier.weight(1f))
                StatsDetailColumn(
                    state = state,
                    period = period,
                    trend = trend,
                    viewModel = viewModel,
                    onMedalClick = { selectedMedal = it },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StatsOverviewColumn(state = state)
                StatsDetailColumn(
                    state = state,
                    period = period,
                    trend = trend,
                    viewModel = viewModel,
                    onMedalClick = { selectedMedal = it },
                )
            }
        }
    }
    selectedMedal?.let { medal ->
        MedalDetailDialog(
            medal = medal,
            progress = state.medalProgress[medal.key],
            unlockedDate = state.unlockedDates[medal.key],
            onDismiss = { selectedMedal = null },
        )
    }
}

/**
 * 统计概览栏（左/上）：汇总卡 2x2、完成率环 + 最长连续、本周/本月、打卡记录。
 * 同时供窄屏单列与宽屏双栏复用。
 */
@Composable
private fun StatsOverviewColumn(
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
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

        // 完成率 & 最长连续
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RateRingCard(rate = state.completionRate, modifier = Modifier.weight(1f))
            StatCard(
                emoji = "🏆",
                value = stringResource(R.string.stats_longest_streak_value, state.longestStreakDays),
                label = stringResource(R.string.stats_longest_streak),
                modifier = Modifier.weight(1f),
            )
        }

        // 本周 / 本月
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                emoji = "📆",
                value = "${state.thisWeekCompleted}",
                label = stringResource(R.string.stats_week_completed),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                emoji = "🗓️",
                value = "${state.thisMonthCompleted}",
                label = stringResource(R.string.stats_month_completed),
                modifier = Modifier.weight(1f),
            )
        }

        // 打卡记录
        SectionTitle(stringResource(R.string.stats_checkin_section))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                emoji = "✅",
                value = "${state.checkInDays}",
                label = stringResource(R.string.stats_checkin_days),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                emoji = "🔥",
                value = stringResource(R.string.stats_streak_value, state.checkInStreak),
                label = stringResource(R.string.stats_checkin_streak),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                emoji = "🕓",
                value = state.recentCheckIn ?: "—",
                label = stringResource(R.string.stats_recent_checkin),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 统计明细栏（右/下）：完成趋势、热力图、分组完成、勋章墙。
 * 同时供窄屏单列与宽屏双栏复用。
 */
@Composable
private fun StatsDetailColumn(
    state: StatsUiState,
    period: StatsPeriod,
    trend: List<TrendBucket>,
    viewModel: StatsViewModel,
    onMedalClick: (Medal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
        MedalWall(
            unlocked = state.unlockedMedals,
            progress = state.medalProgress,
            onMedalClick = onMedalClick,
        )
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
private fun RateRingCard(
    rate: Float,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProgressRing(progress = rate, size = 56.dp)
            Text(
                stringResource(R.string.stats_today_rate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
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

/** 勋章详情：名称 + 解锁条件(desc) + 解锁日期（已解锁）或进度（未解锁且可计算）。 */
@Composable
private fun MedalDetailDialog(
    medal: Medal,
    progress: MedalProgress?,
    unlockedDate: Long?,
    onDismiss: () -> Unit,
) {
    val isUnlocked = unlockedDate != null
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        title = { Text("${medal.icon} ${stringResource(medal.nameRes)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(medal.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isUnlocked) {
                    val dateStr = unlockedDate?.let { formatUnlockDate(it) } ?: ""
                    Text(
                        stringResource(R.string.medal_unlocked_date, dateStr),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.medal_locked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress != null && progress.target > 0 && progress.current >= 0) {
                        val frac = (progress.current.toFloat() / progress.target).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            stringResource(R.string.medal_progress, progress.current, progress.target),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

private fun formatUnlockDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
