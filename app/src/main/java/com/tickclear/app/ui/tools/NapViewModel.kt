package com.tickclear.app.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 午休小憩 ViewModel（V2.9++）：仅维护上次选择的时长（DataStore 记忆）。
 * 闹钟为临时一次性，不在此持久化开关。
 */
@HiltViewModel
class NapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _durationMin = MutableStateFlow(SettingsRepository.DEFAULT_NAP_DURATION_MIN)
    val durationMin: StateFlow<Int> = _durationMin.asStateFlow()

    init {
        viewModelScope.launch { _durationMin.value = settings.napLastDurationMin.first() }
    }

    fun setDuration(min: Int) {
        viewModelScope.launch {
            settings.setNapLastDurationMin(min)
            _durationMin.value = min
        }
    }
}
