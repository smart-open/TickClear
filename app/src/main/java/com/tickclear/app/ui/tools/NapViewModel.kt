package com.tickclear.app.ui.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.scheduler.NapScheduler
import com.tickclear.app.domain.tools.NapPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小憩会话中的白噪音阶段。用于睡眠页如实反映「还在响 / 正在渐隐 / 已进入纯静默」。
 * 与 [com.tickclear.app.domain.tools.NapPlaybackService] 的时间线一致，由同一组时间戳推导，
 * 无需跨进程回调（服务自停后 UI 依旧能正确显示静默态）。
 */
enum class NapPhase { NONE, PLAYING, FADING, SILENT }

/**
 * 午休小憩 ViewModel（V2.9++）：维护上次选择的时长 / 白噪音偏好（DataStore 记忆），
 * 并管理一次小憩会话（调度唤醒闹钟 + 按需启动白噪音前台服务 + 1Hz 倒计时）。
 * 闹钟为临时一次性，不在此持久化开关；会话状态为内存态（进程重建即视为未开始）。
 */
@HiltViewModel
class NapViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _durationMin = MutableStateFlow(SettingsRepository.DEFAULT_NAP_DURATION_MIN)
    val durationMin: StateFlow<Int> = _durationMin.asStateFlow()

    private val _noiseEnabled = MutableStateFlow(SettingsRepository.DEFAULT_NAP_NOISE_ENABLED)
    val noiseEnabled: StateFlow<Boolean> = _noiseEnabled.asStateFlow()

    private val _scene = MutableStateFlow(SettingsRepository.DEFAULT_NAP_NOISE_SCENE)
    val scene: StateFlow<String> = _scene.asStateFlow()

    private val _fadeMin = MutableStateFlow(SettingsRepository.DEFAULT_NAP_FADE_MIN)
    val fadeMin: StateFlow<Int> = _fadeMin.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _endAtMs = MutableStateFlow(0L)
    val endAtMs: StateFlow<Long> = _endAtMs.asStateFlow()

    private val _remainingSec = MutableStateFlow(0)
    val remainingSec: StateFlow<Int> = _remainingSec.asStateFlow()

    private val _phase = MutableStateFlow(NapPhase.NONE)
    val phase: StateFlow<NapPhase> = _phase.asStateFlow()

    /** 白噪音（含渐隐）彻底结束的时刻；0 表示本次未开启白噪音。 */
    private var noiseEndAtMs = 0L

    /** 渐隐起点；0 表示本次不渐隐。 */
    private var fadeStartAtMs = 0L

    init {
        viewModelScope.launch {
            _durationMin.value = settings.napLastDurationMin.first()
            _noiseEnabled.value = settings.napNoiseEnabled.first()
            _scene.value = settings.napNoiseScene.first()
            _fadeMin.value = settings.napFadeMin.first()
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_active.value && _endAtMs.value > 0) {
                    val now = System.currentTimeMillis()
                    _remainingSec.value = ((_endAtMs.value - now) / 1000).toInt().coerceAtLeast(0)
                    _phase.value = computePhase(now)
                }
            }
        }
    }

    /** 由时间戳推导当前白噪音阶段（与前台服务时间线一致）。 */
    private fun computePhase(now: Long): NapPhase {
        if (!_noiseEnabled.value || noiseEndAtMs <= 0L) return NapPhase.NONE
        return when {
            now >= noiseEndAtMs -> NapPhase.SILENT
            fadeStartAtMs in 1..now -> NapPhase.FADING
            else -> NapPhase.PLAYING
        }
    }

    fun setDuration(min: Int) {
        viewModelScope.launch {
            settings.setNapLastDurationMin(min)
            _durationMin.value = min
        }
    }

    fun setNoiseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNapNoiseEnabled(enabled)
            _noiseEnabled.value = enabled
        }
    }

    fun setScene(scene: String) {
        viewModelScope.launch {
            settings.setNapNoiseScene(scene)
            _scene.value = scene
        }
    }

    fun setFadeMin(min: Int) {
        viewModelScope.launch {
            settings.setNapFadeMin(min)
            _fadeMin.value = min
        }
    }

    /** 开始小憩：调度唤醒闹钟，并按需启动白噪音前台服务；进入会话态。 */
    fun start(context: Context) {
        val dur = _durationMin.value
        val endAt = System.currentTimeMillis() + dur * 60_000L
        viewModelScope.launch {
            NapScheduler.schedule(context, dur)
            if (_noiseEnabled.value) {
                val intent = Intent(context, NapPlaybackService::class.java).apply {
                    putExtra(NapPlaybackService.EXTRA_SCENE, _scene.value)
                    putExtra(NapPlaybackService.EXTRA_DURATION_MIN, dur)
                    putExtra(NapPlaybackService.EXTRA_FADE_MIN, _fadeMin.value)
                    putExtra(NapPlaybackService.EXTRA_END_AT, endAt)
                }
                runCatching { ContextCompat.startForegroundService(context, intent) }
            }
            computeNoiseTimeline(dur, endAt)
            _endAtMs.value = endAt
            _remainingSec.value = dur * 60
            _active.value = true
            _phase.value = computePhase(System.currentTimeMillis())
        }
    }

    /**
     * 复刻前台服务的三段式时间线：满音量 → 渐隐 → 纯静默。
     * 渐隐在唤醒前 fadeMs 结束，之后仅保留唤醒闹钟；钳位保证渐隐段不会越过会话起点。
     */
    private fun computeNoiseTimeline(durationMin: Int, endAt: Long) {
        if (!_noiseEnabled.value) {
            noiseEndAtMs = 0L
            fadeStartAtMs = 0L
            return
        }
        val fadeMinRaw = _fadeMin.value
        val fadeMin = if (fadeMinRaw < durationMin) fadeMinRaw else durationMin
        val fadeMs = fadeMin.coerceAtLeast(0) * 60_000L
        if (fadeMs <= 0L) {
            noiseEndAtMs = endAt
            fadeStartAtMs = 0L
            return
        }
        val now = System.currentTimeMillis()
        noiseEndAtMs = (endAt - fadeMs).coerceAtLeast(now)
        fadeStartAtMs = (noiseEndAtMs - fadeMs).coerceAtLeast(now)
    }

    /** 结束小憩：取消闹钟并停止白噪音服务，退出会话态。 */
    fun cancel(context: Context) {
        viewModelScope.launch {
            NapScheduler.cancel(context)
            // 已进入纯静默段时服务早已自停，再发 stopIntent 只会把它拉起来再关一次——白白闪一下通知。
            val noisePlaying = _noiseEnabled.value &&
                noiseEndAtMs > 0L &&
                System.currentTimeMillis() < noiseEndAtMs
            if (noisePlaying) {
                runCatching { context.startService(NapPlaybackService.stopIntent(context)) }
            }
            _active.value = false
            _endAtMs.value = 0
            _remainingSec.value = 0
            noiseEndAtMs = 0L
            fadeStartAtMs = 0L
            _phase.value = NapPhase.NONE
        }
    }
}
