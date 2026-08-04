package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.CheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CheckInEntity)

    @Query("SELECT * FROM check_in WHERE dateLocal = :date")
    suspend fun getByDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_in ORDER BY dateLocal ASC")
    suspend fun getAll(): List<CheckInEntity>

    @Query("SELECT dateLocal FROM check_in ORDER BY dateLocal ASC")
    fun observeDates(): Flow<List<String>>

    @Query("DELETE FROM check_in WHERE dateLocal = :date")
    suspend fun delete(date: String)
}
