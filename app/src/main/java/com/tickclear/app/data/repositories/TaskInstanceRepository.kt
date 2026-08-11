package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.TaskInstanceDao
import com.tickclear.app.domain.model.Task
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.domain.conflict.dueMinutesForDate
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.conflict.shouldGenerateInstance
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.TaskRepository
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

    /** 备份导出：带用户状态（已完成 / 已跳过）的实例，status=0 的可懒生成故不导出。 */
    suspend fun allWithState(): List<TaskInstanceEntity> = dao.getAllWithState()

    /** 备份恢复：整行覆盖，避免本地懒生成的空实例吞掉备份里的完成态。 */
    suspend fun restore(instance: TaskInstanceEntity) = dao.replace(instance)

    /**
     * 懒生成：为指定日期补上所有「启用且应发生」任务的实例（基于传入任务列表，避免重复全量查询）。
     * 子日级重复（每 N 小时）会在当天生成多个实例（各自不同 minute）。视图打开时调用即可。
     * upsert IGNORE 保证幂等；单实例任务沿用 "${taskId}@${date}" 旧 id 以兼容既有提醒/通知。
     */
    suspend fun ensureInstancesForDate(date: LocalDate, tasks: List<Task>) {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        for (task in tasks.filter { it.isEnabled() }) {
            if (!shouldGenerateInstance(task, date)) continue
            val minutes = task.dueMinutesForDate(date)
            if (minutes.isEmpty()) {
                // 全天 / 无具体时刻（随时任务）等：生成恰好一个 dueMinute=null 的实例，
                // 使其在「今日」可见、可完成、可纳入统计；无具体时刻故不排程定点提醒（ReminderScheduler 已跳过 null）。
                dao.upsert(
                    TaskInstanceEntity(
                        id = "${task.id}@$dateStr",
                        taskId = task.id,
                        dueDateLocal = dateStr,
                        dueMinute = null,
                    ),
                )
                continue
            }
            val single = minutes.size == 1
            for (min in minutes) {
                val id = if (single) "${task.id}@$dateStr" else "${task.id}@$dateStr@$min"
                dao.upsert(
                    TaskInstanceEntity(
                        id = id,
                        taskId = task.id,
                        dueDateLocal = dateStr,
                        dueMinute = min,
                    ),
                )
            }
        }
    }

    /** 懒生成（重载：自行查询全部任务）。性能敏感路径请优先调用 [ensureInstancesForDate(date, tasks)]。 */
    suspend fun ensureInstancesForDate(date: LocalDate) {
        ensureInstancesForDate(date, taskRepository.observeAll().first())
    }

    /**
     * 标记实例完成：实例已存在则直接置完成态（保留既有 dueMinute）；
     * 仅当实例缺失（防御性场景）才以 fallbackDueMinute 创建后再完成。
     * CompletionLog 由调用方写入。
     */
    suspend fun completeInstance(instanceId: String, taskId: String, dateStr: String, fallbackDueMinute: Int?) {
        if (dao.getById(instanceId) == null) {
            dao.upsert(TaskInstanceEntity(id = instanceId, taskId = taskId, dueDateLocal = dateStr, dueMinute = fallbackDueMinute))
        }
        dao.setCompleted(instanceId)
    }

    /**
     * 撤销实例完成态：回到 active 并清空 completedAt。
     * 实例不存在时无需补建（本就等价于未完成），CompletionLog 由调用方在同一事务内删除。
     */
    suspend fun uncompleteInstance(instanceId: String) = dao.setPending(instanceId)

    suspend fun get(taskId: String, date: LocalDate): TaskInstanceEntity? =
        dao.get(taskId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))

    suspend fun deleteByTask(taskId: String) = dao.deleteByTask(taskId)

    /** 编辑任务后清掉「今天及以后、未完成」的旧实例，使新的时间/重复规则立即生效（保留历史与已完成记录）。 */
    suspend fun deletePendingFrom(taskId: String, from: LocalDate = LocalDate.now()) =
        dao.deletePendingFrom(taskId, from.format(DateTimeFormatter.ISO_LOCAL_DATE))

    /** 清除所有已软删任务遗留的实例（不区分保留期，仅用于「清空回收站」等全量场景）。 */
    suspend fun purgeDeleted() = dao.deleteForDeletedTasks()

    /**
     * 回收站定期清理调用：只清除已过保留期（deletedAt < [cutoff]）任务的实例。
     * 仍在回收站保留期内的任务实例必须保留，否则还原后完成记录丢失。
     */
    suspend fun purgeExpired(cutoff: Long) = dao.deleteForExpiredTasks(cutoff)

    /**
     * 跳过某次实例（重复任务）：实例已存在则直接置 skipped；不存在则写入 skipped 实例。
     * 兼容单实例 id（"$taskId@$date"）与子日级多实例 id（"$taskId@$date@$min"）：
     * 取 '@' 前段解析日期，解析失败降级为今日，避免 LocalDate.parse 抛异常（M3）。
     * 修复：子日级多实例 id 原会因 upsert IGNORE 未真正置 skipped（实例已存在）。
     */
    suspend fun skip(instanceId: String, taskId: String, date: String) {
        val dateStr = runCatching {
            LocalDate.parse(date.substringBefore("@")).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull() ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (dao.getById(instanceId) == null) {
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
