package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskInstanceDao
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.conflict.shouldGenerateInstance
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskInstanceRepository @Inject constructor(
    private val dao: TaskInstanceDao,
    private val taskRepository: TaskRepository,
) {
    fun observeOn(date: LocalDate): Flow<List<TaskInstanceEntity>> =
        dao.observeOn(date.format(DateTimeFormatter.ISO_LOCAL_DATE))

    /** 写入/更新单个实例（幂等，依赖唯一主键）。 */
    suspend fun upsert(instance: TaskInstanceEntity) = dao.upsert(instance)

    /**
     * 懒生成：为指定日期补上所有「启用且应发生」任务的实例（基于传入任务列表，避免重复全量查询）。
     * 视图打开时调用即可（无需后台调度器）。upsert IGNORE 保证幂等。
     */
    suspend fun ensureInstancesForDate(date: LocalDate, tasks: List<TaskEntity>) {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        for (task in tasks.filter { it.isEnabled() }) {
            if (shouldGenerateInstance(task, date)) {
                dao.upsert(
                    TaskInstanceEntity(
                        id = "${task.id}@$dateStr",
                        taskId = task.id,
                        dueDateLocal = dateStr,
                        dueMinute = task.instanceDueMinute(),
                    ),
                )
            }
        }
    }

    /** 懒生成（重载：自行查询全部任务）。性能敏感路径请优先调用 [ensureInstancesForDate(date, tasks)]。 */
    suspend fun ensureInstancesForDate(date: LocalDate) {
        ensureInstancesForDate(date, taskRepository.observeAll().first())
    }

    /** 标记实例完成（仅置实例完成态，CompletionLog 由调用方写入）。 */
    suspend fun complete(instance: TaskInstanceEntity) {
        dao.setCompleted(instance.id)
    }

    suspend fun get(taskId: String, date: LocalDate): TaskInstanceEntity? =
        dao.get(taskId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))

    suspend fun deleteByTask(taskId: String) = dao.deleteByTask(taskId)

    /** 回收站清理后调用：清除已软删任务遗留的实例。 */
    suspend fun purgeDeleted() = dao.deleteForDeletedTasks()

    /**
     * 跳过某次实例（重复任务）：不存在则直接写入 skipped 实例，存在则置 skipped。
     * instanceId 回退串（如 "$taskId@today"）导致 date 非法时，降级为「今日 + skipped」，
     * 避免 LocalDate.parse 抛 DateTimeParseException 被上层吞掉（M3）。
     */
    suspend fun skip(instanceId: String, taskId: String, date: String) {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        val dateStr = parsed?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val existing = if (parsed != null) get(taskId, parsed) else null
        if (existing == null) {
            dao.upsert(
                TaskInstanceEntity(
                    id = instanceId,
                    taskId = taskId,
                    dueDateLocal = dateStr,
                    dueMinute = null,
                    status = TaskStatus.SKIPPED.code,
                ),
            )
        } else {
            dao.setSkipped(instanceId)
        }
    }
}
