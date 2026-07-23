package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val dao: TaskGroupDao,
    private val taskDao: TaskDao,
) : GroupRepository {
    override fun observeActive(): Flow<List<TaskGroupEntity>> = dao.observeActive()
    override fun observeDeleted(): Flow<List<TaskGroupEntity>> = dao.observeDeleted()
    override suspend fun getById(id: String): TaskGroupEntity? = dao.getById(id)
    override suspend fun upsert(group: TaskGroupEntity) { dao.insert(group) }
    override suspend fun softDelete(id: String) {
        taskDao.detachByGroup(id) // 级联：组内任务脱离组（不丢数据）
        dao.softDelete(id)
    }

    override suspend fun restore(id: String) { dao.restore(id) }
    override suspend fun hardDelete(id: String) { dao.hardDelete(id) }
    override suspend fun purgeExpired(cutoff: Long) { dao.purgeExpired(cutoff) }
}
