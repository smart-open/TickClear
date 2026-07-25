package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VoiceHistoryEntity)

    @Query("SELECT * FROM voice_history ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<VoiceHistoryEntity>>

    @Query("DELETE FROM voice_history")
    suspend fun clearAll()
}
