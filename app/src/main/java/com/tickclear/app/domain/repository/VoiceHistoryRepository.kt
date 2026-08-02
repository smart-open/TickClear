package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import kotlinx.coroutines.flow.Flow

interface VoiceHistoryRepository {
    /** 落库并返回自增主键 rowId（供调用方回填内存对象的真实 ID）。 */
    suspend fun insert(entry: VoiceHistoryEntity): Long
    fun observeAll(): Flow<List<VoiceHistoryEntity>>
    suspend fun clearAll()
    /** V2.8X：删除单条历史（按 role + text 精确匹配）。 */
    suspend fun deleteByTextAndRole(role: String, text: String)
    /** V2.8X++：按主键 id 精确删除单条历史（助手 tab 消息列表同步删库用）。 */
    suspend fun deleteById(id: Long)
}
