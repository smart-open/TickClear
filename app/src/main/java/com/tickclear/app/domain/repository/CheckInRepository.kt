package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.CheckInEntity
import kotlinx.coroutines.flow.Flow

/**
 * 打卡仓库契约（domain 层）。
 */
interface CheckInRepository {
    suspend fun checkIn(dateLocal: String)
    suspend fun upsert(entity: CheckInEntity)
    suspend fun getByDate(dateLocal: String): CheckInEntity?
    suspend fun getAll(): List<CheckInEntity>
    fun observeDates(): Flow<List<String>>
}
