package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.VoiceMemoDao
import com.tickclear.app.data.local.entities.VoiceMemoEntity
import com.tickclear.app.domain.repository.VoiceMemoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceMemoRepositoryImpl @Inject constructor(
    private val dao: VoiceMemoDao,
) : VoiceMemoRepository {
    override suspend fun insert(entry: VoiceMemoEntity): Long = dao.insert(entry)
    override fun observeAll(): Flow<List<VoiceMemoEntity>> = dao.observeAll()
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
