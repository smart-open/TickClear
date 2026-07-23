package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class TodayItem(
    val task: TaskEntity,
    val instanceId: String,
    val done: Boolean,
)

data class TodayTasks(
    val items: List<TodayItem>,
    val conflictIds: Set<String>, // 冲突的 instanceId 集合
    val total: Int,
    val done: Int,
)

/** 今日任务：基于 TaskInstance 当日实例派生（active + completed），并标出时间窗冲突项。 */
@Singleton
class GetTodayTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val instanceRepository: TaskInstanceRepository,
    private val conflictChecker: ConflictChecker,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodayTasks> =
        taskRepository.observeAll().flatMapLatest { allTasks ->
            flow {
                val today = LocalDate.now()
                // 懒生成当日实例（幂等），复用已在流中持有的 allTasks，避免二次全量查询（L4 性能）
                instanceRepository.ensureInstancesForDate(today, allTasks)
                val instances = instanceRepository.observeOn(today).first()

                val taskMap = allTasks.associateBy { it.id }
                val items = instances.mapNotNull { inst ->
                    val task = taskMap[inst.taskId] ?: return@mapNotNull null
                    TodayItem(task = task, instanceId = inst.id, done = inst.status == TaskStatus.COMPLETED.code)
                }.sortedWith(
                    // 未完成在前、已完成下沉；同组内再按当日发生时间排序。
                    compareBy<TodayItem> { it.done }
                        .thenBy { it.task.instanceDueMinute() ?: Int.MAX_VALUE },
                )

                // 冲突检测：以实例的当日时间窗为准（无开始时间的不参与）。
                // 跨午夜任务（结束分钟 < 开始分钟）视为延伸到次日（+1440），避免晚间接续漏判。
                val windows = items.mapNotNull { item ->
                    val start = item.task.instanceDueMinute() ?: return@mapNotNull null
                    val rawEnd = item.task.scheduledEndMin ?: (start + 30)
                    val end = if (rawEnd < start) rawEnd + 1440 else rawEnd
                    item.instanceId to (start..end)
                }
                val conflictIds = conflictChecker.findConflictIds(windows)

                emit(
                    TodayTasks(
                        items = items,
                        conflictIds = conflictIds,
                        total = items.size,
                        done = items.count { it.done },
                    ),
                )
            }
        }
}
