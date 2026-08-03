package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 单条情绪打卡记录。持久化格式：epochDay|心情码|备注（备注可含 '|'，解析用 limit=3）。 */
data class MoodEntry(val epochDay: Long, val code: Int, val note: String)

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<MoodEntry>>(emptyList())
    val entries: StateFlow<List<MoodEntry>> = _entries.asStateFlow()

    private val _today = MutableStateFlow<MoodEntry?>(null)
    val today: StateFlow<MoodEntry?> = _today.asStateFlow()

    init {
        viewModelScope.launch {
            settings.moodLog.collect { raw ->
                val list = parse(raw)
                _entries.value = list
                val day = LocalDate.now().toEpochDay()
                _today.value = list.firstOrNull { it.epochDay == day }
            }
        }
    }

    /** 覆盖式写入今日心情（同日期去重）。 */
    fun saveToday(code: Int, note: String) {
        val day = LocalDate.now().toEpochDay()
        val next = (_entries.value.filter { it.epochDay != day } + MoodEntry(day, code, note))
            .sortedBy { it.epochDay }
        persist(next)
    }

    fun deleteDay(epochDay: Long) {
        persist(_entries.value.filter { it.epochDay != epochDay })
    }

    private fun parse(raw: String): List<MoodEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split("|", limit = 3)
            val day = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val code = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val note = p.getOrNull(2) ?: ""
            MoodEntry(day, code, note)
        }.sortedBy { it.epochDay }.toList()
    }

    private fun persist(list: List<MoodEntry>) {
        val raw = list.joinToString("\n") { "${it.epochDay}|${it.code}|${it.note}" }
        viewModelScope.launch { settings.setMoodLog(raw) }
    }
}
