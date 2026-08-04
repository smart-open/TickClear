package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.CheckInDao
import com.tickclear.app.data.local.entities.CheckInEntity
import com.tickclear.app.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val dao: CheckInDao,
) : CheckInRepository {
    override suspend fun checkIn(dateLocal: String) { dao.insert(CheckInEntity(dateLocal)) }
    override suspend fun upsert(entity: CheckInEntity) { dao.insert(entity) }
    override suspend fun getByDate(dateLocal: String): CheckInEntity? = dao.getByDate(dateLocal)
    override suspend fun getAll(): List<CheckInEntity> = dao.getAll()
    override suspend fun delete(dateLocal: String) { dao.delete(dateLocal) }
    override fun observeDates(): Flow<List<String>> = dao.observeDates()
}
