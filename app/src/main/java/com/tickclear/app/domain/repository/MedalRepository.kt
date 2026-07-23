package com.tickclear.app.domain.repository

import com.tickclear.app.data.local.entities.MedalUnlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * 勋章解锁仓库契约（domain 层）。
 */
interface MedalRepository {
    suspend fun unlock(key: String)
    suspend fun upsert(entity: MedalUnlockEntity)
    suspend fun isUnlocked(key: String): Boolean
    suspend fun all(): List<MedalUnlockEntity>

    /** 实时观察已解锁勋章 key 集合。 */
    fun observeUnlocked(): Flow<List<String>>

    /** 实时观察已解锁勋章 key -> 解锁时间戳（用于勋章详情展示解锁日期）。 */
    fun observeUnlockedDates(): Flow<Map<String, Long>>
}
