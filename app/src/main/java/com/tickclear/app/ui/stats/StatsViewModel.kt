package com.tickclear.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.model.MedalProgress
import com.tickclear.app.domain.usecase.GetStatsUseCase
import com.tickclear.app.domain.usecase.GroupStat
import com.tickclear.app.domain.usecase.StreakUtils
import com.tickclear.app.domain.usecase.TaskStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

/** 统计趋势的聚合周期。 */
enum class StatsPeriod { DAY, WEEK, MONTH }

/** 单个趋势柱：标签 + 完成数。 */
data class TrendBucket(val label: String, val count: Int)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalCompleted: Int = 0,
    val todayCompleted: Int = 0,
    val todayTotal: Int = 0,
    val completionRate: Float = 0f,
    val streakDays: Int = 0,
    val checkInDays: Int = 0,
    val longestStreakDays: Int = 0,
    val checkInStreak: Int = 0,
    val recentCheckIn: String? = null,
    val thisWeekCompleted: Int = 0,
    val thisMonthCompleted: Int = 0,
    val byGroup: List<GroupStat> = emptyList(),
    /** dateLocal(yyyy-MM-dd) -> 当天完成数，用于热力图与趋势。 */
    val completions: Map<String, Int> = emptyMap(),
    val unlockedMedals: Set<String> = emptySet(),
    val unlockedDates: Map<String, Long> = emptyMap(),
    val medalProgress: Map<String, MedalProgress> = emptyMap(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStatsUseCase: GetStatsUseCase,
    private val completionRepository: CompletionRepository,
    private val medalRepository: MedalRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {

    private val completionsFlow = completionRepository.observeAll()
        .map { list -> list.groupingBy { it.dateLocal }.eachCount() }
        .catch { e ->
            // 任一上游流异常（如 Room 查询/实例生成失败）不应让统计页崩溃，降级为空映射。
            android.util.Log.e("StatsViewModel", "completionsFlow failed, fallback empty", e)
            emit(emptyMap())
        }

    val uiState: StateFlow<StatsUiState> = combine(
        getStatsUseCase(),
        completionsFlow,
        checkInRepository.observeDates(),
        medalRepository.observeUnlockedDates(),
    ) { stats, completions, checkIns, unlockedDates ->
        val ciStreak = StreakUtils.computeStreak(checkIns)
        val ym = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0, 7)
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekCount = completions.filterKeys { d ->
            runCatching { LocalDate.parse(d) >= weekStart }.getOrDefault(false)
        }.values.sum()
        val monthCount = completions.filterKeys { it.startsWith(ym) }.values.sum()
        StatsUiState(
            isLoading = false,
            totalCompleted = stats.totalCompleted,
            todayCompleted = stats.todayCompleted,
            todayTotal = stats.todayTotal,
            completionRate = stats.completionRate,
            streakDays = stats.streakDays,
            checkInDays = checkIns.size,
            longestStreakDays = computeLongestStreak(completions),
            checkInStreak = ciStreak,
            recentCheckIn = checkIns.maxOrNull(),
            thisWeekCompleted = weekCount,
            thisMonthCompleted = monthCount,
            byGroup = stats.byGroup,
            completions = completions,
            unlockedMedals = unlockedDates.keys,
            unlockedDates = unlockedDates,
            medalProgress = computeMedalProgress(stats, completions, stats.streakDays),
        )
    }.catch { e ->
        // 整条统计聚合流异常时降级为安全空状态，避免 UI 崩溃；真实异常已打 logcat 供定位。
        android.util.Log.e("StatsViewModel", "stats combine failed, fallback safe state", e)
        emit(StatsUiState(isLoading = false))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    private val _period = MutableStateFlow(StatsPeriod.DAY)
    val period: StateFlow<StatsPeriod> = _period

    fun setPeriod(p: StatsPeriod) {
        _period.value = p
    }

    val trend: StateFlow<List<TrendBucket>> = combine(_period, completionsFlow) { p, completions ->
        computeTrend(p, completions)
    }.catch { e ->
        android.util.Log.e("StatsViewModel", "trend failed, fallback empty", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun computeTrend(period: StatsPeriod, completions: Map<String, Int>): List<TrendBucket> {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return when (period) {
            // V2.8X：日柱底部坐标只显「日」（1~30），不再用 M/d 让 7 月每天都被「7」开头遮蔽。
            StatsPeriod.DAY -> (0 until 30).map { i ->
                val d = today.minusDays(i.toLong())
                val key = d.format(fmt)
                TrendBucket("${d.dayOfMonth}", completions[key] ?: 0)
            }.reversed()

            // 周柱：起点日期用「M-d」更易读（一个月内仍可能撞月）。
            StatsPeriod.WEEK -> (0 until 12).map { i ->
                val ref = today.minusDays(i.toLong() * 7)
                val weekStart = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                var sum = 0
                for (k in 0..6) {
                    sum += completions[weekStart.plusDays(k.toLong()).format(fmt)] ?: 0
                }
                TrendBucket("${weekStart.monthValue}-${weekStart.dayOfMonth}", sum)
            }.reversed()

            // 月柱：保持「X月」，由 TrendBars 渲染。
            StatsPeriod.MONTH -> (0 until 6).map { i ->
                val d = today.minusMonths(i.toLong())
                val key = "${d.year}-${String.format(Locale.ROOT, "%02d", d.monthValue)}"
                val sum = completions.filterKeys { it.startsWith(key) }.values.sum()
                TrendBucket("${d.monthValue}", sum)
            }.reversed()
        }
    }

    /** 连续完成天数的最长跨度（基于 CompletionLog 日期去重，count>0 的日期）。 */
    private fun computeLongestStreak(completions: Map<String, Int>): Int {
        val dates = completions.filter { it.value > 0 }.keys.sorted()
        if (dates.size <= 1) return dates.size
        var best = 1
        var cur = 1
        for (i in 1 until dates.size) {
            val prev = runCatching { LocalDate.parse(dates[i - 1]) }.getOrNull()
            val now = runCatching { LocalDate.parse(dates[i]) }.getOrNull()
            if (prev == null || now == null) {
                cur = 1
                continue
            }
            cur = if (now == prev.plusDays(1)) cur + 1 else 1
            if (cur > best) best = cur
        }
        return best
    }

    /**
     * 各勋章当前进度（current/target）；current<0 表示当前环境无法直接计算（详情页仅展示条件）。
     * 注意：STREAK_3/7 与 [CheckMedalsUseCase] 一致，基于「完成」连续天数（stats.streakDays），非打卡连续。
     */
    private fun computeMedalProgress(
        stats: TaskStats,
        completions: Map<String, Int>,
        completionStreak: Int,
    ): Map<String, MedalProgress> {
        val ym = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0, 7)
        val maxDay = completions.values.maxOrNull() ?: 0
        val monthCount = completions.filterKeys { it.startsWith(ym) }.values.sum()
        return mapOf(
            "FIRST_TASK" to MedalProgress(stats.totalCompleted, 1),
            "STREAK_3" to MedalProgress(completionStreak, 3),
            "STREAK_7" to MedalProgress(completionStreak, 7),
            "LIGHTNING" to MedalProgress(maxDay, 5),
            "ONTIME_10" to MedalProgress(-1, 10),
            "RATE_100" to MedalProgress(
                if (stats.todayTotal > 0 && stats.todayCompleted == stats.todayTotal) 1 else 0, 1,
            ),
            "GROUPS_3" to MedalProgress(stats.byGroup.size, 3),
            "MONTH_MVP" to MedalProgress(monthCount, 30),
        )
    }
}
