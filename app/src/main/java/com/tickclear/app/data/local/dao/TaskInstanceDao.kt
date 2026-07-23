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

    /** 当日全部实例（含已完成），按开始分钟排序。 */
    @Query("SELECT * FROM task_instance WHERE dueDateLocal = :date ORDER BY dueMinute ASC")
    fun observeOn(date: String): Flow<List<TaskInstanceEntity>>

    @Query("SELECT * FROM task_instance WHERE taskId = :taskId AND dueDateLocal = :date LIMIT 1")
    suspend fun get(taskId: String, date: String): TaskInstanceEntity?

    @Query("UPDATE task_instance SET status = 2, completedAt = :ts WHERE id = :id")
    suspend fun setCompleted(id: String, ts: Long = System.currentTimeMillis())

    /** 跳过本次实例（重复任务）：保留任务、不计入完成，当日不再提醒。 */
    @Query("UPDATE task_instance SET status = 3 WHERE id = :id")
    suspend fun setSkipped(id: String)

    @Query("SELECT * FROM task_instance WHERE dueDateLocal >= :from AND dueDateLocal <= :to AND status = 2 ORDER BY completedAt ASC")
    fun observeCompletedBetween(from: String, to: String): Flow<List<TaskInstanceEntity>>

    /** 删除某任务的所有实例（级联软删/硬删任务时调用）。 */
    @Query("DELETE FROM task_instance WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    /** 清理软删任务遗留的实例。 */
    @Query("DELETE FROM task_instance WHERE taskId IN (SELECT id FROM task WHERE deletedAt IS NOT NULL)")
    suspend fun deleteForDeletedTasks()
}
