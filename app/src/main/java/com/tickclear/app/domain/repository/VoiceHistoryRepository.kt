package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import kotlinx.coroutines.flow.Flow

interface VoiceHistoryRepository {
    suspend fun insert(entry: VoiceHistoryEntity)
    fun observeAll(): Flow<List<VoiceHistoryEntity>>
    suspend fun clearAll()
    /** V2.8X：删除单条历史（按 role + text 精确匹配）。 */
    suspend fun deleteByTextAndRole(role: String, text: String)
    /** V2.8X++：按主键 id 精确删除单条历史（助手 tab 消息列表同步删库用）。 */
    suspend fun deleteById(id: Long)
}
