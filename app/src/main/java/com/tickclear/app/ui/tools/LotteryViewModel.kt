package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _options = MutableStateFlow<List<String>>(emptyList())
    val options: StateFlow<List<String>> = _options.asStateFlow()

    init {
        viewModelScope.launch {
            settings.lotteryOptions.collect { text ->
                _options.value = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }

    fun addOption(raw: String) {
        val s = raw.trim()
        if (s.isBlank()) return
        val next = _options.value + s
        _options.value = next
        persist(next)
    }

    fun removeOption(index: Int) {
        val next = _options.value.toMutableList().apply { if (index in indices) removeAt(index) }
        _options.value = next
        persist(next)
    }

    fun clearOptions() {
        _options.value = emptyList()
        persist(emptyList())
    }

    private fun persist(list: List<String>) {
        viewModelScope.launch { settings.setLotteryOptions(list.joinToString("\n")) }
    }
}
