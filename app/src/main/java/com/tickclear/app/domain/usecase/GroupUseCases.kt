package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroup) = repo.upsert(group)
}

@Singleton
class UpdateGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroup) = repo.upsert(group)
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
