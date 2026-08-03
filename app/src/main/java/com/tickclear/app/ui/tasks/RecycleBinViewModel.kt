package com.tickclear.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.RecycleBinRepository
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.domain.usecase.RestoreGroupCascadeUseCase
import com.tickclear.app.domain.usecase.SoftDeleteTaskUseCase
import com.tickclear.app.domain.usecase.SoftDeleteGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repo: RecycleBinRepository,
    private val restoreGroupCascade: RestoreGroupCascadeUseCase,
    private val softDeleteTask: SoftDeleteTaskUseCase,
    private val softDeleteGroup: SoftDeleteGroupUseCase,
) : ViewModel() {
    val items: StateFlow<List<RecycleBinItem>> = repo.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 选择态：以 "${type}:${id}" 作复合键，避免 task/group id 空间重叠导致误选。
    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()
    val selectionMode: StateFlow<Boolean> = _selectedKeys.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val selectedCount: StateFlow<Int> = _selectedKeys.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun keyOf(item: RecycleBinItem) = "${item.type}:${item.id}"

    fun toggleSelection(item: RecycleBinItem) {
        val k = keyOf(item)
        _selectedKeys.value = _selectedKeys.value.toMutableSet().apply {
            if (contains(k)) remove(k) else add(k)
        }
    }

    fun selectAll(items: List<RecycleBinItem>) {
        _selectedKeys.value = items.map { keyOf(it) }.toSet()
    }

    fun clearSelection() {
        _selectedKeys.value = emptySet()
    }

    // 单条恢复后保留对象，供 UI 弹出「撤销」——撤销即重新软删。
    private val _lastRestored = MutableStateFlow<RecycleBinItem?>(null)
    val lastRestored: StateFlow<RecycleBinItem?> = _lastRestored.asStateFlow()

    fun restore(item: RecycleBinItem) {
        viewModelScope.launch {
            if (item.type == "task") repo.restoreTask(item.id) else restoreGroupCascade(item.id)
            _lastRestored.value = item
        }
    }

    fun undoRestore() {
        viewModelScope.launch {
            _lastRestored.value?.let { item ->
                if (item.type == "task") softDeleteTask(item.id) else softDeleteGroup(item.id)
            }
            _lastRestored.value = null
        }
    }

    fun clearLastRestored() {
        _lastRestored.value = null
    }

    fun restoreSelected(items: List<RecycleBinItem>) {
        viewModelScope.launch {
            val sel = _selectedKeys.value
            items.filter { keyOf(it) in sel }.forEach { restore(it) }
            _lastRestored.value = null
            _selectedKeys.value = emptySet()
        }
    }

    fun purgeSelected(items: List<RecycleBinItem>) {
        viewModelScope.launch {
            val sel = _selectedKeys.value
            items.filter { keyOf(it) in sel }.forEach { purge(it) }
            _selectedKeys.value = emptySet()
        }
    }

    fun purge(item: RecycleBinItem) {
        viewModelScope.launch {
            if (item.type == "task") repo.purgeTask(item.id) else repo.purgeGroup(item.id)
        }
    }

    /** 清空回收站：强制清理所有软删记录（级联清实例）。 */
    fun purgeAll() {
        viewModelScope.launch {
            repo.purgeExpired(System.currentTimeMillis() + 1)
        }
    }
}
