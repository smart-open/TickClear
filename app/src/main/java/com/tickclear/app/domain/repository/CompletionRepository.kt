package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.CompletionLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 完成记录仓库契约（domain 层）。
 */
interface CompletionRepository {
    fun observeAll(): Flow<List<CompletionLogEntity>>
    fun observeRange(from: String, to: String): Flow<List<CompletionLogEntity>>
    fun observeDates(): Flow<List<String>>
    suspend fun insert(log: CompletionLogEntity)
    suspend fun countByDate(date: String): Int
}
