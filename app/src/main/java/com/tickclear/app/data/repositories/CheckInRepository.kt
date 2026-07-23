package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.CheckInDao
import com.tickclear.app.data.local.entities.CheckInEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepository @Inject constructor(
    private val dao: CheckInDao,
) {
    suspend fun checkIn(dateLocal: String) = dao.insert(CheckInEntity(dateLocal))
    suspend fun upsert(entity: CheckInEntity) = dao.insert(entity)
    suspend fun getByDate(dateLocal: String) = dao.getByDate(dateLocal)
    suspend fun getAll() = dao.getAll()
    fun observeDates(): Flow<List<String>> = dao.observeDates()
}
