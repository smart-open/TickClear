package com.tickclear.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.RecycleBinRepository
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.domain.usecase.RestoreGroupCascadeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repo: RecycleBinRepository,
    private val restoreGroupCascade: RestoreGroupCascadeUseCase,
) : ViewModel() {
    val items: StateFlow<List<RecycleBinItem>> = repo.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(item: RecycleBinItem) {
        viewModelScope.launch {
            if (item.type == "task") repo.restoreTask(item.id) else restoreGroupCascade(item.id)
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
