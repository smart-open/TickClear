package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.data.repositories.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroupEntity) = repo.upsert(group)
}

@Singleton
class UpdateGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroupEntity) = repo.upsert(group)
}

/** 软删组：组内任务脱离组（保留数据），不物理删除。 */
@Singleton
class SoftDeleteGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(id: String) = repo.softDelete(id)
}

@Singleton
class RestoreGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(id: String) = repo.restore(id)
}
