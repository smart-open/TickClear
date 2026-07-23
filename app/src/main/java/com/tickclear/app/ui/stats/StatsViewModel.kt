package com.tickclear.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.data.repositories.CheckInRepository
import com.tickclear.app.data.repositories.CompletionRepository
import com.tickclear.app.data.repositories.MedalRepository
import com.tickclear.app.domain.usecase.GetStatsUseCase
import com.tickclear.app.domain.usecase.GroupStat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
    val byGroup: List<GroupStat> = emptyList(),
    /** dateLocal(yyyy-MM-dd) -> 当天完成数，用于热力图与趋势。 */
    val completions: Map<String, Int> = emptyMap(),
    val unlockedMedals: Set<String> = emptySet(),
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

    val uiState: StateFlow<StatsUiState> = combine(
        getStatsUseCase(),
        completionsFlow,
        medalRepository.observeUnlocked(),
        checkInRepository.observeDates(),
    ) { stats, completions, unlocked, checkIns ->
        StatsUiState(
            isLoading = false,
            totalCompleted = stats.totalCompleted,
            todayCompleted = stats.todayCompleted,
            todayTotal = stats.todayTotal,
            completionRate = stats.completionRate,
            streakDays = stats.streakDays,
            checkInDays = checkIns.size,
            byGroup = stats.byGroup,
            completions = completions,
            unlockedMedals = unlocked.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    private val _period = MutableStateFlow(StatsPeriod.DAY)
    val period: StateFlow<StatsPeriod> = _period

    fun setPeriod(p: StatsPeriod) {
        _period.value = p
    }

    val trend: StateFlow<List<TrendBucket>> = combine(_period, completionsFlow) { p, completions ->
        computeTrend(p, completions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun computeTrend(period: StatsPeriod, completions: Map<String, Int>): List<TrendBucket> {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return when (period) {
            StatsPeriod.DAY -> (0 until 30).map { i ->
                val d = today.minusDays(i.toLong())
                val key = d.format(fmt)
                TrendBucket("${d.monthValue}/${d.dayOfMonth}", completions[key] ?: 0)
            }.reversed()

            StatsPeriod.WEEK -> (0 until 12).map { i ->
                val ref = today.minusDays(i.toLong() * 7)
                val weekStart = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                var sum = 0
                for (k in 0..6) {
                    sum += completions[weekStart.plusDays(k.toLong()).format(fmt)] ?: 0
                }
                TrendBucket("${weekStart.monthValue}/${weekStart.dayOfMonth}", sum)
            }.reversed()

            StatsPeriod.MONTH -> (0 until 6).map { i ->
                val d = today.minusMonths(i.toLong())
                val key = "${d.year}-${String.format("%02d", d.monthValue)}"
                val sum = completions.filterKeys { it.startsWith(key) }.values.sum()
                TrendBucket("${d.monthValue}", sum)
            }.reversed()
        }
    }
}
