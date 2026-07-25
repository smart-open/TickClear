package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.VoiceHistoryDao
import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import com.tickclear.app.domain.repository.VoiceHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceHistoryRepositoryImpl @Inject constructor(
    private val dao: VoiceHistoryDao,
) : VoiceHistoryRepository {
    override suspend fun insert(entry: VoiceHistoryEntity) = dao.insert(entry)
    override fun observeAll(): Flow<List<VoiceHistoryEntity>> = dao.observeAll()
    override suspend fun clearAll() = dao.clearAll()
}
