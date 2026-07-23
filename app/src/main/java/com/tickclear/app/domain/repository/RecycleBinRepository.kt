package com.tickclear.app.domain.repository

import com.tickclear.app.domain.model.RecycleBinItem
import kotlinx.coroutines.flow.Flow

/**
 * 回收站仓库契约（domain 层）。
 */
interface RecycleBinRepository {
    fun observeItems(): Flow<List<RecycleBinItem>>
    suspend fun restoreTask(id: String)
    suspend fun restoreGroup(id: String)
    suspend fun purgeTask(id: String)
    suspend fun purgeGroup(id: String)
    suspend fun purgeExpired(cutoff: Long)
}
