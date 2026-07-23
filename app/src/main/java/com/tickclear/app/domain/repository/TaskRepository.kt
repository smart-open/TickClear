package com.tickclear.app.domain.repository

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

/**
 * 任务仓库契约（domain 层）。UI/domain 仅依赖此接口，具体实现见 data 层。
 */
interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    fun observeByGroup(groupId: String): Flow<List<Task>>
    fun observeDeleted(): Flow<List<Task>>
    suspend fun getById(id: String): Task?
    suspend fun getActiveById(id: String): Task?
    suspend fun upsert(task: Task)
    suspend fun setStatus(id: String, status: TaskStatus, completedAt: Long?)
    suspend fun softDelete(id: String)
    suspend fun restore(id: String)
    suspend fun hardDelete(id: String)
    suspend fun purgeExpired(cutoff: Long)
}
