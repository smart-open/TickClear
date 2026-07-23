package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedalUnlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MedalUnlockEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM medal_unlock WHERE medalKey = :key)")
    suspend fun isUnlocked(key: String): Boolean

    @Query("SELECT * FROM medal_unlock")
    suspend fun getAll(): List<MedalUnlockEntity>

    @Query("SELECT * FROM medal_unlock")
    fun observeAll(): Flow<List<MedalUnlockEntity>>
}
