package com.tickclear.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.scheduler.ReminderScheduler
import com.tickclear.app.domain.scheduler.GeofenceScheduler
import com.tickclear.app.domain.usecase.AddGroupUseCase
import com.tickclear.app.domain.usecase.AddTaskUseCase
import com.tickclear.app.domain.usecase.RestoreTaskUseCase
import com.tickclear.app.domain.usecase.SoftDeleteGroupUseCase
import com.tickclear.app.domain.usecase.SoftDeleteTaskUseCase
import com.tickclear.app.domain.usecase.UpdateGroupUseCase
import com.tickclear.app.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val tasks: List<Task> = emptyList(),     // 仅启用（未软删）任务
    val groups: List<TaskGroup> = emptyList(), // 仅启用任务组
    val pendingDelete: Task? = null,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val groupRepository: GroupRepository,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val softDeleteTaskUseCase: SoftDeleteTaskUseCase,
    private val restoreTaskUseCase: RestoreTaskUseCase,
    private val addGroupUseCase: AddGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val softDeleteGroupUseCase: SoftDeleteGroupUseCase,
    private val geofenceScheduler: GeofenceScheduler,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val pendingDelete = MutableStateFlow<Task?>(null)

    val uiState: StateFlow<TasksUiState> = combine(
        taskRepository.observeAll(),
        groupRepository.observeActive(),
        pendingDelete,
    ) { tasks, groups, pending ->
        TasksUiState(tasks = tasks, groups = groups, pendingDelete = pending)
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        TasksUiState(),
    )

    fun deleteTask(id: String) {
        viewModelScope.launch {
            val task = taskRepository.getById(id)
            softDeleteTaskUseCase(id)
            pendingDelete.value = task
            ReminderScheduler.cancelForTask(appContext, id)
            geofenceScheduler.unregister(id)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            pendingDelete.value?.let { restoreTaskUseCase(it.id) }
            pendingDelete.value = null
        }
    }

    fun clearPending() {
        pendingDelete.value = null
    }

    /** 新建或更新任务；返回冲突列表（供编辑页红条提示）。 */
    suspend fun saveTask(task: Task): List<Task> {
        val existing = taskRepository.getById(task.id)
        val conflicts = if (existing != null) {
            updateTaskUseCase(task)
        } else {
            addTaskUseCase(task).conflicts
        }
        ReminderScheduler.cancelForTask(appContext, task.id)
        if (task.reminderEnabled) {
            ReminderScheduler.scheduleForTask(appContext, task)
        }
        geofenceScheduler.unregister(task.id)
        if (task.geoLat != null && task.geoLng != null && task.geoRadius != null) {
            geofenceScheduler.register(task)
        }
        return conflicts
    }

    /** 新建或更新任务组。 */
    suspend fun saveGroup(group: TaskGroup) {
        if (groupRepository.getById(group.id) != null) {
            updateGroupUseCase(group)
        } else {
            addGroupUseCase(group)
        }
    }

    /** 软删任务组：组内任务脱离组（不丢数据）。 */
    fun deleteGroup(id: String) {
        viewModelScope.launch { softDeleteGroupUseCase(id) }
    }
}
