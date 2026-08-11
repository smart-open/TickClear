package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.data.repositories.mapper.mapList
import com.tickclear.app.data.repositories.mapper.toDomain
import com.tickclear.app.data.repositories.mapper.toEntity
import com.tickclear.app.domain.backup.TransactionRunner
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val dao: TaskGroupDao,
    private val taskDao: TaskDao,
    private val txn: TransactionRunner,
) : GroupRepository {
    override fun observeActive(): Flow<List<TaskGroup>> = dao.observeActive().mapList { it.toDomain() }
    override fun observeDeleted(): Flow<List<TaskGroup>> = dao.observeDeleted().mapList { it.toDomain() }
    override suspend fun getById(id: String): TaskGroup? = dao.getById(id)?.toDomain()
    override suspend fun upsert(group: TaskGroup) { dao.insert(group.toEntity()) }
    /**
     * 软删分组：组内任务先脱离该组，再软删组本身。
     *
     * 两步必须同事务：若「任务脱组」已提交而「软删组」失败，任务的 groupId 已被永久置空、
     * 组却仍然存活，分组归属静默丢失且无法从软删记录还原。
     */
    override suspend fun softDelete(id: String) = txn.run {
        taskDao.detachByGroup(id) // 级联：组内任务脱离组（不丢数据）
        dao.softDelete(id)
    }

    override suspend fun restore(id: String) { dao.restore(id) }
    override suspend fun hardDelete(id: String) { dao.hardDelete(id) }
    override suspend fun purgeExpired(cutoff: Long) { dao.purgeExpired(cutoff) }
}
