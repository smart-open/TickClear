package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tickclear.app.data.local.entities.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(h: HabitEntity)

    @Update
    suspend fun update(h: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE habit SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY orderIndex ASC, createdAt ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?
}
