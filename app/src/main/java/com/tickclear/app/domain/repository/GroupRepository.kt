package com.tickclear.app.domain.repository

import com.tickclear.app.domain.model.TaskGroup
import kotlinx.coroutines.flow.Flow

/**
 * 分组仓库契约（domain 层）。
 */
interface GroupRepository {
    fun observeActive(): Flow<List<TaskGroup>>
    fun observeDeleted(): Flow<List<TaskGroup>>
    suspend fun getById(id: String): TaskGroup?
    suspend fun upsert(group: TaskGroup)
    suspend fun softDelete(id: String)
    suspend fun restore(id: String)
    suspend fun hardDelete(id: String)
    suspend fun purgeExpired(cutoff: Long)
}
