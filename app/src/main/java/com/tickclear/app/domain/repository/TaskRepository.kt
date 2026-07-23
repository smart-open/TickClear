package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

/**
 * 任务仓库契约（domain 层）。UI/domain 仅依赖此接口，具体实现见 data 层。
 */
interface TaskRepository {
    fun observeAll(): Flow<List<TaskEntity>>
    fun observeByGroup(groupId: String): Flow<List<TaskEntity>>
    fun observeDeleted(): Flow<List<TaskEntity>>
    suspend fun getById(id: String): TaskEntity?
    suspend fun getActiveById(id: String): TaskEntity?
    suspend fun upsert(task: TaskEntity)
    suspend fun setStatus(id: String, status: TaskStatus, completedAt: Long?)
    suspend fun softDelete(id: String)
    suspend fun restore(id: String)
    suspend fun hardDelete(id: String)
    suspend fun purgeExpired(cutoff: Long)
}
