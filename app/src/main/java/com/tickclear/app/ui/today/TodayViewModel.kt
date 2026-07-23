package com.tickclear.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.scheduler.ReminderScheduler
import com.tickclear.app.domain.scheduler.GeofenceScheduler
import com.tickclear.app.domain.usecase.AddTaskUseCase
import com.tickclear.app.domain.usecase.CompleteTaskUseCase
import com.tickclear.app.domain.usecase.GetTodayTasksUseCase
import com.tickclear.app.domain.usecase.SoftDeleteTaskUseCase
import com.tickclear.app.domain.usecase.RestoreTaskUseCase
import com.tickclear.app.domain.usecase.TodayItem
import com.tickclear.app.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val items: List<TodayItem> = emptyList(),
    val conflictIds: Set<String> = emptySet(),
    val total: Int = 0,
    val done: Int = 0,
    val groups: Map<String, TaskGroup> = emptyMap(),
    val encouragement: String = "",
    val pendingDelete: Task? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getTodayTasks: GetTodayTasksUseCase,
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val softDeleteTaskUseCase: SoftDeleteTaskUseCase,
    private val restoreTaskUseCase: RestoreTaskUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val geofenceScheduler: GeofenceScheduler,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val pendingDelete = MutableStateFlow<Task?>(null)
    private val encouragementFlow = MutableStateFlow("")

    val uiState: StateFlow<TodayUiState> = combine(
        getTodayTasks(),
        groupRepository.observeActive(),
        pendingDelete,
        encouragementFlow,
    ) { today, groups, pending, enc ->
        TodayUiState(
            items = today.items,
            conflictIds = today.conflictIds,
            total = today.total,
            done = today.done,
            groups = groups.associateBy { it.id },
            encouragement = enc,
            pendingDelete = pending,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), TodayUiState())

    init {
        refreshEncouragement()
    }

    fun complete(item: TodayItem) {
        viewModelScope.launch {
            completeTaskUseCase(item.task)
            ReminderScheduler.cancelForTask(appContext, item.task.id)
        }
    }

    fun delete(item: TodayItem) {
        viewModelScope.launch {
            softDeleteTaskUseCase(item.task.id)
            pendingDelete.value = item.task
            ReminderScheduler.cancelForTask(appContext, item.task.id)
            geofenceScheduler.unregister(item.task.id)
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

    /** 一键清空：将今日所有未完成任务标记为完成（复用 complete 逻辑 + 记打卡）。 */
    fun clearAll() {
        viewModelScope.launch {
            val incomplete = getTodayTasks().first().items.filter { !it.done }
            incomplete.forEach { item ->
                completeTaskUseCase(item.task)
                ReminderScheduler.cancelForTask(appContext, item.task.id)
            }
        }
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

    fun refreshEncouragement() {
        viewModelScope.launch {
            val list = appContext.resources.getStringArray(com.tickclear.app.R.array.encouragement_list)
            if (list.isNotEmpty()) {
                encouragementFlow.value = list.random()
            }
        }
    }
}
