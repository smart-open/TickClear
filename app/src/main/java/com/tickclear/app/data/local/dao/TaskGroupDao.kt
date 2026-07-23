package com.tickclear.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tickclear.app.data.local.entities.TaskGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: TaskGroupEntity)

    @Update
    suspend fun update(group: TaskGroupEntity)

    @Query("UPDATE task_group SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE task_group SET deletedAt = NULL, status = 0, updatedAt = :ts WHERE id = :id")
    suspend fun restore(id: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM task_group WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM task_group WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeExpired(cutoff: Long)

    @Query("SELECT * FROM task_group WHERE deletedAt IS NULL ORDER BY orderIndex ASC")
    fun observeActive(): Flow<List<TaskGroupEntity>>

    @Query("SELECT * FROM task_group WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<TaskGroupEntity>>

    @Query("SELECT * FROM task_group WHERE id = :id")
    suspend fun getById(id: String): TaskGroupEntity?
}
