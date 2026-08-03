package com.tickclear.app.data.repositories

import com.tickclear.app.data.local.dao.ExpiryDao
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.domain.repository.ExpiryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpiryRepositoryImpl @Inject constructor(
    private val dao: ExpiryDao,
) : ExpiryRepository {
    override fun observeAll(): Flow<List<ExpiryEntity>> = dao.observeAll()
    override suspend fun insert(entry: ExpiryEntity): Long = dao.insert(entry)
    override suspend fun update(entry: ExpiryEntity) = dao.update(entry)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun getById(id: Long): ExpiryEntity? = dao.getById(id)
    override suspend fun getEnabled(): List<ExpiryEntity> = dao.getEnabled()
}
