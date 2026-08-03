package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.ExpiryEntity
import kotlinx.coroutines.flow.Flow

/** 到期提醒仓库契约（domain 层）。 */
interface ExpiryRepository {
    fun observeAll(): Flow<List<ExpiryEntity>>
    suspend fun insert(entry: ExpiryEntity): Long
    suspend fun update(entry: ExpiryEntity)
    suspend fun deleteById(id: Long)
    suspend fun getById(id: Long): ExpiryEntity?
    /** 仅取开启提醒的条目（供 [ExpiryScheduler.rescheduleAll] 重排）。 */
    suspend fun getEnabled(): List<ExpiryEntity>
}
