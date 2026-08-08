package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 打卡补录 ViewModel（V2.9++）：为习惯或任务完成记录补打 / 取消过往任意日期的记录。
 * 底层 HabitRepository.checkIn / CheckInRepository 本就支持任意日期，本 VM 仅开放显式入口。
 */
@HiltViewModel
class BackfillViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {

    val habits: Flow<List<Habit>> = habitRepository.observeHabits()

    suspend fun habitChecked(habitId: String, date: String): Boolean =
        habitRepository.isChecked(habitId, date)

    suspend fun setHabitChecked(habitId: String, date: String, checked: Boolean) {
        if (checked) habitRepository.checkIn(habitId, date)
        else habitRepository.uncheck(habitId, date)
    }

    suspend fun dailyChecked(date: String): Boolean =
        checkInRepository.getByDate(date) != null

    suspend fun setDailyChecked(date: String, checked: Boolean) {
        if (checked) checkInRepository.checkIn(date)
        else checkInRepository.delete(date)
    }

    /** 供 UI 直接触发（不关心返回）。 */
    fun flipHabit(habitId: String, date: String, checked: Boolean) {
        viewModelScope.launch { setHabitChecked(habitId, date, checked) }
    }

    fun flipDaily(date: String, checked: Boolean) {
        viewModelScope.launch { setDailyChecked(date, checked) }
    }
}
