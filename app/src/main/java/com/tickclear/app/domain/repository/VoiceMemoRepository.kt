package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.VoiceMemoEntity
import kotlinx.coroutines.flow.Flow

interface VoiceMemoRepository {
    suspend fun insert(entry: VoiceMemoEntity): Long
    fun observeAll(): Flow<List<VoiceMemoEntity>>
    suspend fun deleteById(id: Long)
}
