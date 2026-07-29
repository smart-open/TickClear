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

    /**
     * V2.8X：按 (role, text) 精确删除单条历史。
     * Room 参数顺序按 @Query 占位符出现顺序传入；role 与 text 共同保证「同文本同角色」的同一记录被命中。
     */
    @Query("DELETE FROM voice_history WHERE role = :role AND text = :text")
    suspend fun deleteByTextAndRole(role: String, text: String)

    /**
     * V2.8X++：按主键 id 精确删除单条历史。供助手 tab 消息列表（带 DB id）同步删库用——
     * 旧 `deleteByTextAndRole` 在「system 消息也落库」后会出现同文本多条记录，无法精确删单条。
     */
    @Query("DELETE FROM voice_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
