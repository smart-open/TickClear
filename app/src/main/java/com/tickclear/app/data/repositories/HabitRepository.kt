package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.HabitCheckInDao
import com.tickclear.app.data.local.dao.HabitDao
import com.tickclear.app.data.local.entities.HabitCheckInEntity
import com.tickclear.app.data.local.entities.HabitEntity
import com.tickclear.app.domain.backup.TransactionRunner
import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val checkInDao: HabitCheckInDao,
    private val txn: TransactionRunner,
) : HabitRepository {

    override fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeCheckinDates(habitId: String): Flow<List<String>> =
        checkInDao.observeDates(habitId)

    override suspend fun getCheckinDates(habitId: String): List<String> = checkInDao.getDates(habitId)

    override suspend fun isChecked(habitId: String, date: String): Boolean =
        checkInDao.exists(habitId, date) != null

    override suspend fun checkIn(habitId: String, date: String) {
        checkInDao.insert(HabitCheckInEntity(habitId, date))
    }

    override suspend fun uncheck(habitId: String, date: String) {
        checkInDao.delete(habitId, date)
    }

    override suspend fun createHabit(habit: Habit) { habitDao.insert(habit.toEntity()) }

    override suspend fun updateHabit(habit: Habit) { habitDao.update(habit.toEntity()) }

    override suspend fun getHabit(id: String): Habit? = habitDao.getById(id)?.toDomain()

    /**
     * 删除习惯：先清空其全部打卡记录，再删习惯本体。
     *
     * 两步必须同事务：若打卡记录已删而习惯删除失败，习惯仍在列表里但历史被清空、
     * 连续天数归零，且打卡记录无法恢复。
     */
    override suspend fun deleteHabit(habitId: String) = txn.run {
        checkInDao.deleteAllForHabit(habitId)
        habitDao.delete(habitId)
    }

    override suspend fun archiveHabit(habitId: String) { habitDao.archive(habitId) }

    private fun HabitEntity.toDomain() = Habit(
        id = id,
        title = title,
        emoji = emoji,
        repeatDays = repeatDays,
        reminderMin = reminderMin,
        colorIndex = colorIndex,
        createdAt = createdAt,
        archived = archived,
        orderIndex = orderIndex,
    )

    private fun Habit.toEntity() = HabitEntity(
        id = id,
        title = title,
        emoji = emoji,
        repeatDays = repeatDays,
        reminderMin = reminderMin,
        colorIndex = colorIndex,
        createdAt = createdAt,
        archived = archived,
        orderIndex = orderIndex,
    )
}
