package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 烹饪多组定时器（工具箱「烹饪定时」，V2.9++）。
 * 支持同时运行多组倒计时；任一组归零时通过 [finished] 事件流通知 UI 触发振动+提示音。
 */
@HiltViewModel
class CookingTimerViewModel @Inject constructor() : ViewModel() {

    data class Timer(
        val id: String,
        val name: String,
        val totalSec: Int,
        val remainSec: Int,
        val running: Boolean,
        val finished: Boolean,
    )

    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers.asStateFlow()

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        var changed = false
        val next = _timers.value.map { t ->
            if (t.running && t.remainSec > 0) {
                changed = true
                val r = t.remainSec - 1
                if (r <= 0) {
                    _finished.tryEmit(t.name)
                    t.copy(remainSec = 0, running = false, finished = true)
                } else {
                    t.copy(remainSec = r)
                }
            } else {
                t
            }
        }
        if (changed) _timers.value = next
    }

    /**
     * 新增一组倒计时。
     *
     * @param name 计时名称，为空时使用 [fallbackName]（由 UI 层从资源解析，避免 VM 硬编码中文）。
     */
    fun add(name: String, sec: Int, fallbackName: String) {
        if (sec <= 0) return
        val t = Timer(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { fallbackName },
            totalSec = sec,
            remainSec = sec,
            running = false,
            finished = false,
        )
        _timers.value = _timers.value + t
    }

    fun start(id: String) = update(id) {
        if (it.remainSec > 0) it.copy(running = true, finished = false) else it
    }

    fun pause(id: String) = update(id) { it.copy(running = false) }

    fun reset(id: String) = update(id) {
        it.copy(remainSec = it.totalSec, running = false, finished = false)
    }

    fun remove(id: String) {
        _timers.value = _timers.value.filter { it.id != id }
    }

    private fun update(id: String, f: (Timer) -> Timer) {
        _timers.value = _timers.value.map { if (it.id == id) f(it) else it }
    }
}
