package com.tickclear.app.ui.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.scheduler.CountdownEvent
import com.tickclear.app.domain.scheduler.CountdownScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CountdownViewModel @Inject constructor(
    application: Application,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    private val _events = MutableStateFlow<List<CountdownEvent>>(emptyList())
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            settings.countdownEvents.collect { raw ->
                _events.value = raw.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { CountdownEvent.parse(it) }
                    .sortedBy { it.targetEpochMs }
            }
        }
    }

    fun add(name: String, epochMs: Long) {
        viewModelScope.launch {
            val newEvent = CountdownEvent(
                id = "cd_${UUID.randomUUID()}",
                name = name.trim(),
                targetEpochMs = epochMs,
            )
            val newList = (_events.value + newEvent).sortedBy { it.targetEpochMs }
            _events.value = newList
            settings.setCountdownEvents(CountdownEvent.serialize(newList))
            applySchedule(newList)
        }
    }

    fun remove(event: CountdownEvent) {
        viewModelScope.launch {
            val newList = _events.value.filter { it.id != event.id }
            _events.value = newList
            settings.setCountdownEvents(CountdownEvent.serialize(newList))
            applySchedule(newList, setOf(event.id))
        }
    }

    fun updateNotify(
        event: CountdownEvent,
        notify: Boolean,
        advanceDays: Int,
        daily: Boolean,
        hour: Int,
        minute: Int,
    ) {
        viewModelScope.launch {
            val updated = event.copy(
                notify = notify,
                advanceDays = advanceDays,
                daily = daily,
                hour = hour,
                minute = minute,
            )
            val newList = _events.value.map { if (it.id == event.id) updated else it }
                .sortedBy { it.targetEpochMs }
            _events.value = newList
            settings.setCountdownEvents(CountdownEvent.serialize(newList))
            // 先取消该事件旧闹钟再重排，避免 notify 开关切换时残留
            applySchedule(newList, setOf(event.id))
        }
    }

    private fun applySchedule(events: List<CountdownEvent>, removed: Set<String> = emptySet()) {
        val ctx = getApplication<Application>().applicationContext
        CountdownScheduler.reschedule(ctx, events, removed)
    }
}
