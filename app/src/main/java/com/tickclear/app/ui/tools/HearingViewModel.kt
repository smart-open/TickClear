package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 听力保护 ViewModel（V2.9++）：仅暴露设置流与 setter，监测逻辑在 [com.tickclear.app.domain.hearing.HearingMonitor]。
 */
@HiltViewModel
class HearingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.hearingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val volumeThreshold: StateFlow<Int> = settings.hearingVolumeThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_HEARING_VOLUME_THRESHOLD)
    val maxWearMin: StateFlow<Int> = settings.hearingMaxWearMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_HEARING_WEAR_MIN)

    fun setEnabled(b: Boolean) = viewModelScope.launch { settings.setHearingEnabled(b) }
    fun setVolumeThreshold(t: Int) = viewModelScope.launch { settings.setHearingVolumeThreshold(t) }
    fun setMaxWearMin(m: Int) = viewModelScope.launch { settings.setHearingMaxWearMin(m) }
}
