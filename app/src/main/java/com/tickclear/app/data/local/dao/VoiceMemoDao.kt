package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.VoiceMemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceMemoDao {
    /**
     * 插入并返回自增主键（供 ViewModel 回填内存态、定位磁盘文件）。
     * 与 voice_history 一致用 REPLACE 兜底（同 id 覆盖）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VoiceMemoEntity): Long

    @Query("SELECT * FROM voice_memos ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<VoiceMemoEntity>>

    @Query("DELETE FROM voice_memos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
