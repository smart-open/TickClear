package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.MedalUnlockDao
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import com.tickclear.app.domain.repository.MedalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedalRepositoryImpl @Inject constructor(
    private val dao: MedalUnlockDao,
) : MedalRepository {
    override suspend fun unlock(key: String) { dao.insert(MedalUnlockEntity(medalKey = key)) }
    override suspend fun upsert(entity: MedalUnlockEntity) { dao.insert(entity) }
    override suspend fun isUnlocked(key: String): Boolean = dao.isUnlocked(key)
    override suspend fun all(): List<MedalUnlockEntity> = dao.getAll()

    /** 实时观察已解锁勋章 key 集合。 */
    override fun observeUnlocked(): Flow<List<String>> =
        dao.observeAll().map { list -> list.map { it.medalKey } }

    /** 实时观察已解锁勋章 key -> 解锁时间戳（用于勋章详情展示解锁日期）。 */
    override fun observeUnlockedDates(): Flow<Map<String, Long>> =
        dao.observeAll().map { list -> list.associate { it.medalKey to it.unlockedAt } }
}
