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

data class CountdownEvent(
    val name: String,
    val targetEpochMs: Long,
)

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<List<CountdownEvent>>(emptyList())
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            settings.countdownEvents.collect { raw ->
                _events.value = raw.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { parse(it) }
                    .sortedBy { it.targetEpochMs }
            }
        }
    }

    fun add(name: String, epochMs: Long) {
        viewModelScope.launch {
            val newList = (_events.value + CountdownEvent(name.trim(), epochMs))
                .sortedBy { it.targetEpochMs }
            _events.value = newList
            settings.setCountdownEvents(serialize(newList))
        }
    }

    fun remove(event: CountdownEvent) {
        viewModelScope.launch {
            val newList = _events.value.filter { it != event }
            _events.value = newList
            settings.setCountdownEvents(serialize(newList))
        }
    }

    private fun serialize(list: List<CountdownEvent>): String =
        list.joinToString("\n") { "${it.name}|${it.targetEpochMs}" }

    private fun parse(line: String): CountdownEvent? {
        val i = line.indexOf('|')
        if (i <= 0) return null
        val name = line.substring(0, i)
        val ms = line.substring(i + 1).toLongOrNull() ?: return null
        return CountdownEvent(name, ms)
    }
}
