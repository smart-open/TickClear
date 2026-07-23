package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    fun observeByGroup(groupId: String): Flow<List<TaskEntity>> = dao.observeByGroup(groupId)
    fun observeDeleted(): Flow<List<TaskEntity>> = dao.observeDeleted()
    suspend fun getById(id: String): TaskEntity? = dao.getById(id)
    suspend fun getActiveById(id: String): TaskEntity? = dao.getActiveById(id)
    suspend fun upsert(task: TaskEntity) = dao.insert(task)
    suspend fun setStatus(id: String, status: TaskStatus, completedAt: Long?) =
        dao.setStatus(id, status.code, completedAt)

    suspend fun softDelete(id: String) = dao.softDelete(id)
    suspend fun restore(id: String) = dao.restore(id)
    suspend fun hardDelete(id: String) = dao.hardDelete(id)
    suspend fun purgeExpired(cutoff: Long) = dao.purgeExpired(cutoff)
}
