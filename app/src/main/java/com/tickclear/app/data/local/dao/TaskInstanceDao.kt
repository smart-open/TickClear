package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskInstanceDao {
    /** 幂等写入：同一 (taskId, dueDateLocal) 重复调用不新增行。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsert(instance: TaskInstanceEntity)

    /**
     * 整行覆盖写入（备份恢复专用）。
     *
     * [upsert] 是 IGNORE 语义，恢复备份时若本地已被懒生成出一条同 id 的空实例，
     * 备份里的「已完成/已跳过」状态会被静默丢弃。恢复路径必须用 REPLACE。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(instance: TaskInstanceEntity)

    /**
     * 备份导出专用：只取带用户状态（已完成 / 已跳过）的实例。
     *
     * status=0 的实例可由 [TaskInstanceRepository.ensureInstancesForDate] 按任务规则重建，
     * 不入备份可避免「每日重复任务 × 一年」把备份体积撑大一个数量级。
     */
    @Query("SELECT * FROM task_instance WHERE status != 0 ORDER BY dueDateLocal ASC")
    suspend fun getAllWithState(): List<TaskInstanceEntity>

    /** 当日全部实例（含已完成），按开始分钟排序。 */
    @Query("SELECT * FROM task_instance WHERE dueDateLocal = :date ORDER BY dueMinute ASC")
    fun observeOn(date: String): Flow<List<TaskInstanceEntity>>

    @Query("SELECT * FROM task_instance WHERE taskId = :taskId AND dueDateLocal = :date LIMIT 1")
    suspend fun get(taskId: String, date: String): TaskInstanceEntity?

    @Query("SELECT * FROM task_instance WHERE id = :id")
    suspend fun getById(id: String): TaskInstanceEntity?

    @Query("UPDATE task_instance SET status = 2, completedAt = :ts WHERE id = :id")
    suspend fun setCompleted(id: String, ts: Long = System.currentTimeMillis())

    /**
     * 撤销完成：实例回到 active 并清空 completedAt，与 [setCompleted] 严格对称。
     * 用于今日页「已完成任务再次点击勾选框恢复未完成」；CompletionLog 的删除由调用方同事务处理。
     */
    @Query("UPDATE task_instance SET status = 0, completedAt = NULL WHERE id = :id")
    suspend fun setPending(id: String)

    /** 跳过本次实例（重复任务）：保留任务、不计入完成，当日不再提醒。 */
    @Query("UPDATE task_instance SET status = 3 WHERE id = :id")
    suspend fun setSkipped(id: String)

    @Query("SELECT * FROM task_instance WHERE dueDateLocal >= :from AND dueDateLocal <= :to AND status = 2 ORDER BY completedAt ASC")
    fun observeCompletedBetween(from: String, to: String): Flow<List<TaskInstanceEntity>>

    /** 删除某任务的所有实例（级联软删/硬删任务时调用）。 */
    @Query("DELETE FROM task_instance WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    /**
     * 清掉某任务「今天及以后、尚未完成/跳过」的实例，供编辑任务后重建用。
     *
     * 实例是懒生成且 `@Insert(IGNORE)` 幂等的：把任务时间从 09:00 改到 20:00 后，
     * 当天那条 dueMinute=540 的旧实例不会被覆盖，于是列表还显示旧时间、提醒也按旧时刻排。
     * 这里只删未完成的当天及未来实例，历史与已完成/已跳过记录原样保留（统计不受影响）。
     */
    @Query("DELETE FROM task_instance WHERE taskId = :taskId AND dueDateLocal >= :from AND status = 0")
    suspend fun deletePendingFrom(taskId: String, from: String)

    /** 清理软删任务遗留的实例。 */
    @Query("DELETE FROM task_instance WHERE taskId IN (SELECT id FROM task WHERE deletedAt IS NOT NULL)")
    suspend fun deleteForDeletedTasks()

    /**
     * 清理「已过保留期、即将被物理删除」的软删任务的实例。
     *
     * 与 [deleteForDeletedTasks] 的区别：后者不看保留期，会把仍躺在回收站里
     * （随时可还原）的任务实例一并清掉，还原后完成记录全丢。回收站定期清理必须用本方法。
     */
    @Query(
        "DELETE FROM task_instance WHERE taskId IN " +
            "(SELECT id FROM task WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff)",
    )
    suspend fun deleteForExpiredTasks(cutoff: Long)
}
