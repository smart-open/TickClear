package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.CompletionLogDao
import com.tickclear.app.data.local.entities.CompletionLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompletionRepository @Inject constructor(
    private val dao: CompletionLogDao,
) {
    fun observeAll(): Flow<List<CompletionLogEntity>> = dao.observeAll()
    fun observeRange(from: String, to: String): Flow<List<CompletionLogEntity>> = dao.observeRange(from, to)
    fun observeDates(): Flow<List<String>> = dao.observeDates()
    suspend fun insert(log: CompletionLogEntity) = dao.insert(log)
    suspend fun countByDate(date: String): Int = dao.countByDate(date)
}
