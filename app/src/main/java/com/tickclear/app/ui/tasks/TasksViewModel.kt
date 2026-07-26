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
import com.tickclear.app.domain.usecase.DeleteGroupCascadeUseCase
import com.tickclear.app.domain.usecase.PauseGroupUseCase
import com.tickclear.app.domain.usecase.ResumeGroupUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val tasks: List<Task> = emptyList(),     // 按标签筛选后的启用（未软删）任务
    val groups: List<TaskGroup> = emptyList(), // 仅启用任务组
    val pendingDelete: Task? = null,
    val allTags: List<String> = emptyList(),   // V2.67 全部在用标签（并集，用于筛选 chips）
    val selectedTags: Set<String> = emptySet(), // V2.67 当前选中的筛选标签（空=不筛选）
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
    private val pauseGroupUseCase: PauseGroupUseCase,
    private val resumeGroupUseCase: ResumeGroupUseCase,
    private val deleteGroupCascadeUseCase: DeleteGroupCascadeUseCase,
    private val geofenceScheduler: GeofenceScheduler,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val pendingDelete = MutableStateFlow<Task?>(null)
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<TasksUiState> = combine(
        taskRepository.observeAll(),
        groupRepository.observeActive(),
        pendingDelete,
        selectedTags,
    ) { tasks, groups, pending, selTags ->
        // allTags 取自全量任务（未筛选），保证 chips 不因筛选而消失。
        val allTags = tasks.flatMap { it.tags }.distinct().sorted()
        // 多选按「任一命中」（并集）过滤；无效标签（已不存在）自动被忽略。
        val filtered = if (selTags.isEmpty()) tasks else tasks.filter { t -> t.tags.any { it in selTags } }
        TasksUiState(
            tasks = filtered,
            groups = groups,
            pendingDelete = pending,
            allTags = allTags,
            selectedTags = selTags,
        )
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        TasksUiState(),
    )

    /** 切换某标签的筛选选中状态。 */
    fun toggleTagFilter(tag: String) {
        selectedTags.value = selectedTags.value.let { if (tag in it) it - tag else it + tag }
    }

    /** 清空标签筛选。 */
    fun clearTagFilter() {
        selectedTags.value = emptySet()
    }

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

    // ── V2.33 组级暂停 / 启用 / 删除级联 ──
    /** 暂停整个任务组：级联暂停组内所有未软删任务。 */
    fun pauseGroup(id: String) {
        viewModelScope.launch {
            pauseGroupUseCase(id)
            cancelGroupReminders(id)
        }
    }

    /** 启用整个任务组：级联恢复组内所有未软删任务为 ACTIVE。 */
    fun resumeGroup(id: String) {
        viewModelScope.launch {
            resumeGroupUseCase(id)
            rescheduleGroupReminders(id)
        }
    }

    /** 删除整个任务组：级联软删组内所有未软删任务，再软删组本身。 */
    fun deleteGroupCascade(id: String) {
        viewModelScope.launch {
            deleteGroupCascadeUseCase(id)
            cancelGroupReminders(id)
        }
    }

    private suspend fun cancelGroupReminders(groupId: String) {
        val tasks = taskRepository.observeByGroup(groupId).first().filter { it.deletedAt == null }
        for (task in tasks) {
            ReminderScheduler.cancelForTask(appContext, task.id)
            geofenceScheduler.unregister(task.id)
        }
    }

    private suspend fun rescheduleGroupReminders(groupId: String) {
        val tasks = taskRepository.observeByGroup(groupId).first().filter { it.deletedAt == null && it.reminderEnabled }
        for (task in tasks) {
            ReminderScheduler.scheduleForTask(appContext, task)
            geofenceScheduler.unregister(task.id)
            if (task.geoLat != null && task.geoLng != null && task.geoRadius != null) {
                geofenceScheduler.register(task)
            }
        }
    }
}
