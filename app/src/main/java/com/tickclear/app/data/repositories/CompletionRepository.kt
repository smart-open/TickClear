package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.CompletionLogDao
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.domain.repository.CompletionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompletionRepositoryImpl @Inject constructor(
    private val dao: CompletionLogDao,
) : CompletionRepository {
    override fun observeAll(): Flow<List<CompletionLogEntity>> = dao.observeAll()
    override fun observeRange(from: String, to: String): Flow<List<CompletionLogEntity>> = dao.observeRange(from, to)
    override fun observeDates(): Flow<List<String>> = dao.observeDates()
    override suspend fun insert(log: CompletionLogEntity) { dao.insert(log) }
    override suspend fun delete(taskId: String, dateLocal: String) { dao.deleteByTaskAndDate(taskId, dateLocal) }
    override suspend fun countByDate(date: String): Int = dao.countByDate(date)
}
