package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tickclear.app.data.local.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE task SET status = :status, completedAt = :completedAt, updatedAt = :ts WHERE id = :id")
    suspend fun setStatus(id: String, status: Int, completedAt: Long?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE task SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE task SET deletedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun restore(id: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE task SET deletedAt = NULL, updatedAt = :ts WHERE groupId = :groupId AND deletedAt IS NOT NULL")
    suspend fun restoreByGroup(groupId: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM task WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeExpired(cutoff: Long)

    @Query("UPDATE task SET groupId = NULL WHERE groupId = :groupId")
    suspend fun detachByGroup(groupId: String)

    /**
     * 让所有引用「即将被物理删除的过期组」的任务先脱离该组。
     * task.groupId 上的外键是 NO_ACTION，直接 DELETE 组会抛 SQLiteConstraintException，
     * 使回收站的 30 天自动清理整体失败（且每次清理都再失败一次）。
     */
    @Query(
        "UPDATE task SET groupId = NULL WHERE groupId IN " +
            "(SELECT id FROM task_group WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff)",
    )
    suspend fun detachFromExpiredGroups(cutoff: Long)

    @Query("UPDATE task SET groupId = NULL WHERE id = :id")
    suspend fun detachFromGroup(id: String)

    @Query("SELECT * FROM task WHERE deletedAt IS NULL AND groupId = :groupId ORDER BY scheduledStartMin ASC")
    fun observeByGroup(groupId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NULL ORDER BY scheduledStartMin ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM task WHERE deletedAt IS NULL AND id = :id")
    suspend fun getActiveById(id: String): TaskEntity?
}
