package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import kotlinx.coroutines.flow.Flow

interface VoiceHistoryRepository {
    suspend fun insert(entry: VoiceHistoryEntity)
    fun observeAll(): Flow<List<VoiceHistoryEntity>>
    suspend fun clearAll()
}
