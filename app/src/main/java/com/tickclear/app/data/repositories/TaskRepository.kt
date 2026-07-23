package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.repositories.mapper.mapList
import com.tickclear.app.data.repositories.mapper.toDomain
import com.tickclear.app.data.repositories.mapper.toEntity
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().mapList { it.toDomain() }
    override fun observeByGroup(groupId: String): Flow<List<Task>> = dao.observeByGroup(groupId).mapList { it.toDomain() }
    override fun observeDeleted(): Flow<List<Task>> = dao.observeDeleted().mapList { it.toDomain() }
    override suspend fun getById(id: String): Task? = dao.getById(id)?.toDomain()
    override suspend fun getActiveById(id: String): Task? = dao.getActiveById(id)?.toDomain()
    override suspend fun upsert(task: Task) { dao.insert(task.toEntity()) }
    override suspend fun setStatus(id: String, newStatus: TaskStatus, completedAt: Long?) {
        dao.setStatus(id, newStatus.code, completedAt)
    }

    override suspend fun softDelete(id: String) { dao.softDelete(id) }
    override suspend fun restore(id: String) { dao.restore(id) }
    override suspend fun hardDelete(id: String) { dao.hardDelete(id) }
    override suspend fun purgeExpired(cutoff: Long) { dao.purgeExpired(cutoff) }
}
