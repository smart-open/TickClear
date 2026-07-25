package com.tickclear.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import com.tickclear.app.domain.repository.VoiceHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceHistoryViewModel @Inject constructor(
    private val repo: VoiceHistoryRepository,
) : ViewModel() {
    val items: StateFlow<List<VoiceHistoryEntity>> = repo.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}
