package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.domain.backup.TransactionRunner
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
    private val txn: TransactionRunner,
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
    /** 彻底删除单个任务：实例与任务行同批提交，避免中途失败留下孤儿实例。 */
    override suspend fun purgeTask(id: String) = txn.run {
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
    override suspend fun purgeGroup(id: String) = txn.run {
        taskDao.detachByGroup(id)
        groupDao.hardDelete(id)
    }

    /**
     * 回收站定期清理：整批放进同一事务，任一步失败全量回滚，不留半清理状态。
     *
     * 顺序要求：
     * 1. 先清实例——清理按「taskId ∈ (已过期软删 task)」定位，若先物理删除 task 行，
     *    子查询返回空集，这些实例将永远删不掉（孤儿行）且被统计计入，完成率长期失真。
     * 2. 只清 `deletedAt < cutoff` 的实例——仍在保留期内的软删任务随时可还原，
     *    其完成记录必须保留。
     * 3. 过期组物理删除前，先让仍存活的成员任务脱离，避免整批清理触发外键约束失败。
     */
    override suspend fun purgeExpired(cutoff: Long) = txn.run {
        taskInstanceRepository.purgeExpired(cutoff)
        taskDao.purgeExpired(cutoff)
        taskDao.detachFromExpiredGroups(cutoff)
        groupDao.purgeExpired(cutoff)
    }
}
