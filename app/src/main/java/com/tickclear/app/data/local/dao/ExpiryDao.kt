package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tickclear.app.data.local.entities.ExpiryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExpiryEntity): Long

    @Update
    suspend fun update(entry: ExpiryEntity)

    @Query("DELETE FROM expiry_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expiry_reminders ORDER BY expire_epoch_day ASC, id ASC")
    fun observeAll(): Flow<List<ExpiryEntity>>

    @Query("SELECT * FROM expiry_reminders WHERE id = :id")
    suspend fun getById(id: Long): ExpiryEntity?

    @Query("SELECT * FROM expiry_reminders WHERE reminder_enabled = 1")
    suspend fun getEnabled(): List<ExpiryEntity>
}
