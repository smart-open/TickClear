package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {
    override fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    override fun observeByGroup(groupId: String): Flow<List<TaskEntity>> = dao.observeByGroup(groupId)
    override fun observeDeleted(): Flow<List<TaskEntity>> = dao.observeDeleted()
    override suspend fun getById(id: String): TaskEntity? = dao.getById(id)
    override suspend fun getActiveById(id: String): TaskEntity? = dao.getActiveById(id)
    override suspend fun upsert(task: TaskEntity) { dao.insert(task) }
    override suspend fun setStatus(id: String, newStatus: TaskStatus, completedAt: Long?) {
        dao.setStatus(id, newStatus.code, completedAt)
    }

    override suspend fun softDelete(id: String) { dao.softDelete(id) }
    override suspend fun restore(id: String) { dao.restore(id) }
    override suspend fun hardDelete(id: String) { dao.hardDelete(id) }
    override suspend fun purgeExpired(cutoff: Long) { dao.purgeExpired(cutoff) }
}
