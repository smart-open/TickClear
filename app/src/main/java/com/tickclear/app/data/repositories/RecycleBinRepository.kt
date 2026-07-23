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

    override suspend fun purgeGroup(id: String) { groupDao.hardDelete(id) }
    override suspend fun purgeExpired(cutoff: Long) {
        taskDao.purgeExpired(cutoff)
        groupDao.purgeExpired(cutoff)
        taskInstanceRepository.purgeDeleted()
    }
}
