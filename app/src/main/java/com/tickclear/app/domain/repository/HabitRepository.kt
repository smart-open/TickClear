package com.tickclear.app.domain.repository

import com.tickclear.app.domain.model.Habit
import kotlinx.coroutines.flow.Flow

/**
 * 习惯仓库契约（V2.69 习惯养成模式）。
 * 习惯与打卡记录分离存储：habit 表保存定义，habit_checkin 表保存每日打卡。
 */
interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    fun observeCheckinDates(habitId: String): Flow<List<String>>
    suspend fun getCheckinDates(habitId: String): List<String>
    suspend fun isChecked(habitId: String, date: String): Boolean
    suspend fun checkIn(habitId: String, date: String)
    suspend fun uncheck(habitId: String, date: String)
    suspend fun createHabit(habit: Habit)
    suspend fun updateHabit(habit: Habit)
    suspend fun getHabit(id: String): Habit?
    suspend fun deleteHabit(habitId: String)
    suspend fun archiveHabit(habitId: String)
}
