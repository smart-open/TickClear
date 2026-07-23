package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.CompletionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {
    /** 幂等写入：(taskId, dateLocal) 唯一，重复完成同一天同一任务不会新增行。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: CompletionLogEntity)

    @Query("SELECT * FROM completion_log WHERE dateLocal >= :from AND dateLocal <= :to ORDER BY completedAt ASC")
    fun observeRange(from: String, to: String): Flow<List<CompletionLogEntity>>

    @Query("SELECT * FROM completion_log ORDER BY completedAt ASC")
    fun observeAll(): Flow<List<CompletionLogEntity>>

    @Query("SELECT COUNT(*) FROM completion_log WHERE dateLocal = :date")
    suspend fun countByDate(date: String): Int

    @Query("SELECT DISTINCT dateLocal FROM completion_log ORDER BY dateLocal ASC")
    fun observeDates(): Flow<List<String>>
}
