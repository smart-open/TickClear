package com.tickclear.app.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 番茄专注计时器：专注 + 休息交替。计时在 ViewModel 内由协程驱动，
 * 经 Compose 的 mutableState 暴露，切后台/旋转不丢失（ViewModelStore 留存）。
 */
@HiltViewModel
class PomodoroViewModel @Inject constructor() : ViewModel() {
    var focusMin by mutableIntStateOf(25)
    var breakMin by mutableIntStateOf(5)
    var remainingSec by mutableIntStateOf(25 * 60)
    var isRunning by mutableStateOf(false)
    var phase by mutableStateOf("focus") // focus | break
    var completed by mutableIntStateOf(0)
    private var job: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        job = viewModelScope.launch {
            while (isRunning && remainingSec > 0) {
                delay(1000L)
                if (!isRunning) break
                remainingSec--
                if (remainingSec <= 0) switchPhase()
            }
            isRunning = false
        }
    }

    private fun switchPhase() {
        if (phase == "focus") {
            completed++
            phase = "break"
            remainingSec = breakMin * 60
        } else {
            phase = "focus"
            remainingSec = focusMin * 60
        }
    }

    fun pause() {
        isRunning = false
        job?.cancel()
        job = null
    }

    fun reset() {
        pause()
        phase = "focus"
        remainingSec = focusMin * 60
    }

    fun setFocus(min: Int) {
        focusMin = min
        if (!isRunning && phase == "focus") remainingSec = min * 60
    }

    fun setBreak(min: Int) {
        breakMin = min
        if (!isRunning && phase == "break") remainingSec = min * 60
    }

    override fun onCleared() {
        job?.cancel()
    }
}
