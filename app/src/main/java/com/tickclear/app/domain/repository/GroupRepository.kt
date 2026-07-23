package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.TaskGroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 分组仓库契约（domain 层）。
 */
interface GroupRepository {
    fun observeActive(): Flow<List<TaskGroupEntity>>
    fun observeDeleted(): Flow<List<TaskGroupEntity>>
    suspend fun getById(id: String): TaskGroupEntity?
    suspend fun upsert(group: TaskGroupEntity)
    suspend fun softDelete(id: String)
    suspend fun restore(id: String)
    suspend fun hardDelete(id: String)
    suspend fun purgeExpired(cutoff: Long)
}
