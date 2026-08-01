package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.domain.repository.RecycleBinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecycleBinRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val groupDao: TaskGroupDao,
    private val taskInstanceRepository: TaskInstanceRepository,
) : RecycleBinRepository {
    override fun observeItems(): Flow<List<RecycleBinItem>> {
        val tasks = taskDao.observeDeleted().map { list ->
            list.map { RecycleBinItem(it.id, "task", it.title, it.deletedAt ?: 0) }
        }
        val groups = groupDao.observeDeleted().map { list ->
            list.map { RecycleBinItem(it.id, "group", it.name, it.deletedAt ?: 0) }
        }
        return combine(tasks, groups) { t, g ->
            (t + g).sortedByDescending { it.deletedAt }
        }
    }

    override suspend fun restoreTask(id: String) { taskDao.restore(id) }
    override suspend fun restoreGroup(id: String) { groupDao.restore(id) }
    override suspend fun purgeTask(id: String) {
        taskInstanceRepository.deleteByTask(id)
        taskDao.hardDelete(id)
    }

    /**
     * 彻底删除任务组。
     *
     * V2.8X 修复：此前直接 hardDelete —— task.groupId 上有 `ForeignKey(NO_ACTION)` 外键，
     * 只要组内还有任何任务（含未软删的、以及软删但未到 30 天的），SQLite 就抛
     * FOREIGN KEY constraint failed，导致「回收站 → 彻底删除组」必崩。
     * 现先把引用该组的任务脱离（groupId 置空），再删组。
     */
    override suspend fun purgeGroup(id: String) {
        taskDao.detachByGroup(id)
        groupDao.hardDelete(id)
    }

    override suspend fun purgeExpired(cutoff: Long) {
        taskDao.purgeExpired(cutoff)
        // 同上：过期组被物理删除前，先让仍存活的成员任务脱离，避免整批清理因外键约束失败。
        taskDao.detachFromExpiredGroups(cutoff)
        groupDao.purgeExpired(cutoff)
        taskInstanceRepository.purgeDeleted()
    }
}
