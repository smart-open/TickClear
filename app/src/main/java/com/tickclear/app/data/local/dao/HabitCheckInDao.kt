package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.HabitCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: HabitCheckInEntity)

    @Query("DELETE FROM habit_checkin WHERE habitId = :habitId AND dateLocal = :date")
    suspend fun delete(habitId: String, date: String)

    @Query("DELETE FROM habit_checkin WHERE habitId = :habitId")
    suspend fun deleteAllForHabit(habitId: String)

    @Query("SELECT dateLocal FROM habit_checkin WHERE habitId = :habitId ORDER BY dateLocal ASC")
    fun observeDates(habitId: String): Flow<List<String>>

    @Query("SELECT dateLocal FROM habit_checkin WHERE habitId = :habitId ORDER BY dateLocal ASC")
    suspend fun getDates(habitId: String): List<String>

    @Query("SELECT COUNT(*) FROM habit_checkin WHERE habitId = :habitId")
    suspend fun count(habitId: String): Int

    @Query("SELECT 1 FROM habit_checkin WHERE habitId = :habitId AND dateLocal = :date LIMIT 1")
    suspend fun exists(habitId: String, date: String): Int?
}
