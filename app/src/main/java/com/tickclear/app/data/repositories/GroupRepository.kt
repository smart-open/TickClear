package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.data.local.entities.TaskGroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val dao: TaskGroupDao,
    private val taskDao: TaskDao,
) {
    fun observeActive(): Flow<List<TaskGroupEntity>> = dao.observeActive()
    fun observeDeleted(): Flow<List<TaskGroupEntity>> = dao.observeDeleted()
    suspend fun getById(id: String): TaskGroupEntity? = dao.getById(id)
    suspend fun upsert(group: TaskGroupEntity) = dao.insert(group)
    suspend fun softDelete(id: String) {
        taskDao.detachByGroup(id) // 级联：组内任务脱离组（不丢数据）
        dao.softDelete(id)
    }
    suspend fun restore(id: String) = dao.restore(id)
    suspend fun hardDelete(id: String) = dao.hardDelete(id)
    suspend fun purgeExpired(cutoff: Long) = dao.purgeExpired(cutoff)
}
