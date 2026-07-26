package com.tickclear.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.repository.HabitRepository
import com.tickclear.app.domain.util.computeStreak
import com.tickclear.app.domain.util.isHabitDueToday
import com.tickclear.app.domain.util.todayLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HabitItem(
    val habit: Habit,
    val todayChecked: Boolean,
    val streak: Int,
    val dueToday: Boolean,
)

data class HabitsUiState(
    val items: List<HabitItem> = emptyList(),
    val isEmpty: Boolean = true,
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
) : ViewModel() {

    private val habits: StateFlow<List<Habit>> = habitRepository.observeHabits().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    /** 每个习惯叠加「今日已打卡」与「连续天数」，习惯列表变化时用 flatMapLatest 重新组合。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HabitsUiState> = habits.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(HabitsUiState(emptyList(), isEmpty = true))
        } else {
            val flows = list.map { h ->
                habitRepository.observeCheckinDates(h.id).map { dates ->
                    HabitItem(
                        habit = h,
                        todayChecked = todayLocal() in dates,
                        streak = computeStreak(dates),
                        dueToday = isHabitDueToday(h),
                    )
                }
            }
            combine(flows) { arr -> HabitsUiState(arr.toList(), isEmpty = false) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())

    fun toggleToday(habitId: String) {
        val today = todayLocal()
        val checked = uiState.value.items.find { it.habit.id == habitId }?.todayChecked == true
        viewModelScope.launch {
            if (checked) habitRepository.uncheck(habitId, today)
            else habitRepository.checkIn(habitId, today)
        }
    }

    fun createHabit(title: String, emoji: String, repeatDays: String) {
        viewModelScope.launch {
            habitRepository.createHabit(
                Habit(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    emoji = emoji.trim(),
                    repeatDays = repeatDays,
                ),
            )
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { habitRepository.deleteHabit(habitId) }
    }
}
