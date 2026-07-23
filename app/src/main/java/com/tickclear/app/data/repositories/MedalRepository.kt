package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.MedalUnlockDao
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedalRepository @Inject constructor(
    private val dao: MedalUnlockDao,
) {
    suspend fun unlock(key: String) = dao.insert(MedalUnlockEntity(medalKey = key))
    suspend fun upsert(entity: MedalUnlockEntity) = dao.insert(entity)
    suspend fun isUnlocked(key: String): Boolean = dao.isUnlocked(key)
    suspend fun all(): List<MedalUnlockEntity> = dao.getAll()

    /** 实时观察已解锁勋章 key 集合。 */
    fun observeUnlocked(): Flow<List<String>> =
        dao.observeAll().map { list -> list.map { it.medalKey } }
}
