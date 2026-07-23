package com.tickclear.app.data.repositories.mapper

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Flow<List<A>> → Flow<List<B>> 的列表映射工具（仓库返回列表流时复用）。 */
fun <A, B> Flow<List<A>>.mapList(transform: (A) -> B): Flow<List<B>> = map { it.map(transform) }

/** TaskEntity → 领域模型 [Task]（仓库读取边界）。 */
fun TaskEntity.toDomain(): Task = Task(
    id = id,
    groupId = groupId,
    title = title,
    notes = notes,
    status = status,
    scheduledStartMin = scheduledStartMin,
    scheduledEndMin = scheduledEndMin,
    allDay = allDay,
    scheduledDate = scheduledDate,
    repeatType = repeatType,
    repeatIntervalDays = repeatIntervalDays,
    repeatIntervalHours = repeatIntervalHours,
    repeatWeekdays = repeatWeekdays,
    repeatMonthDay = repeatMonthDay,
    repeatAnchorMin = repeatAnchorMin,
    repeatAnchorDate = repeatAnchorDate,
    reminderEnabled = reminderEnabled,
    reminderLevel = reminderLevel,
    reminderOffsetMin = reminderOffsetMin,
    source = source,
    geoLat = geoLat,
    geoLng = geoLng,
    geoRadius = geoRadius,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    deletedAt = deletedAt,
)

/** 领域模型 [Task] → TaskEntity（仓库写入边界）。 */
fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    groupId = groupId,
    title = title,
    notes = notes,
    status = status,
    scheduledStartMin = scheduledStartMin,
    scheduledEndMin = scheduledEndMin,
    allDay = allDay,
    scheduledDate = scheduledDate,
    repeatType = repeatType,
    repeatIntervalDays = repeatIntervalDays,
    repeatIntervalHours = repeatIntervalHours,
    repeatWeekdays = repeatWeekdays,
    repeatMonthDay = repeatMonthDay,
    repeatAnchorMin = repeatAnchorMin,
    repeatAnchorDate = repeatAnchorDate,
    reminderEnabled = reminderEnabled,
    reminderLevel = reminderLevel,
    reminderOffsetMin = reminderOffsetMin,
    source = source,
    geoLat = geoLat,
    geoLng = geoLng,
    geoRadius = geoRadius,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    deletedAt = deletedAt,
)

/** TaskGroupEntity → 领域模型 [TaskGroup]（仓库读取边界）。 */
fun TaskGroupEntity.toDomain(): TaskGroup = TaskGroup(
    id = id,
    name = name,
    icon = icon,
    colorKey = colorKey,
    orderIndex = orderIndex,
    repeatType = repeatType,
    repeatAnchorMin = repeatAnchorMin,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/** 领域模型 [TaskGroup] → TaskGroupEntity（仓库写入边界）。 */
fun TaskGroup.toEntity(): TaskGroupEntity = TaskGroupEntity(
    id = id,
    name = name,
    icon = icon,
    colorKey = colorKey,
    orderIndex = orderIndex,
    repeatType = repeatType,
    repeatAnchorMin = repeatAnchorMin,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
