package com.tickclear.app.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 剪贴板防窃取（工具箱「剪贴板保护」，V2.9++，简易版）。
 * 受 Android 10+ 隐私沙箱限制，第三方应用无法直接阻止其他 App 读取剪贴板，
 * 本工具采用「延迟自动清空」策略：开启后，复制的内容会在 N 秒后自动清空，
 * 使后台应用难以在延时之后读到；并提供「安全复制并自动清除」与「立即清除」按钮。
 * 当前进程存活期间生效（屏幕打开即持续监听）。
 */
@HiltViewModel
class ClipboardGuardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _autoClear = MutableStateFlow(false)
    val autoClear: StateFlow<Boolean> = _autoClear.asStateFlow()

    private val _delaySec = MutableStateFlow(30)
    val delaySec: StateFlow<Int> = _delaySec.asStateFlow()

    /** 当前可读的剪贴板预览（Android 10+ 下其他应用的剪贴板不可读，可能为空）。 */
    private val _clipPreview = MutableStateFlow("")
    val clipPreview: StateFlow<String> = _clipPreview.asStateFlow()

    /** 当前剪贴板是否可被本应用读取（false=系统限制/为空）。 */
    private val _readable = MutableStateFlow(true)
    val readable: StateFlow<Boolean> = _readable.asStateFlow()

    /** 自动清除倒计时剩余毫秒（0=无进行中）。 */
    private val _countdownMs = MutableStateFlow(0L)
    val countdownMs: StateFlow<Long> = _countdownMs.asStateFlow()

    private val _lastEvent = MutableStateFlow("")
    val lastEvent: StateFlow<String> = _lastEvent.asStateFlow()

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var clearJob: Job? = null
    /** 标记本次剪贴板变化是否由本工具自身的 setPrimaryClip 触发，避免递归。 */
    private var selfClearing = false

    init {
        settings.clipboardAutoClear
            .onEach { _autoClear.value = it }
            .launchIn(viewModelScope)
        settings.clipboardClearDelaySec
            .onEach { _delaySec.value = it }
            .launchIn(viewModelScope)
        listener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }
        clipboard.addPrimaryClipChangedListener(listener)
        refreshPreview()
    }

    private fun onClipChanged() {
        if (selfClearing) {
            selfClearing = false
            return
        }
        refreshPreview()
        if (_autoClear.value) startCountdown()
    }

    private fun refreshPreview() {
        val text = runCatching {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0)?.text?.toString() else null
        }.getOrNull()
        if (!text.isNullOrEmpty()) {
            _readable.value = true
            _clipPreview.value = text.take(200)
        } else {
            _readable.value = false
            _clipPreview.value = ""
        }
    }

    fun setAutoClear(enabled: Boolean) {
        _autoClear.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { settings.setClipboardAutoClear(enabled) }
        }
    }

    fun setDelaySec(sec: Int) {
        val v = sec.coerceIn(5, 120)
        _delaySec.value = v
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { settings.setClipboardClearDelaySec(v) }
        }
    }

    /** 安全复制：写入剪贴板并立即启动自动清除倒计时（无论开关是否开启）。 */
    fun copyProtect(text: String) {
        if (text.isBlank()) return
        selfClearing = true // 抑制本次写入触发的监听器，避免重复倒计时
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("tickclear", text))
        }.onFailure { e ->
            AppLogger.e("ClipboardGuard", "setPrimaryClip failed", e)
            selfClearing = false
            _lastEvent.value = appContext.getString(R.string.clip_guard_failed)
            return
        }
        _lastEvent.value = appContext.getString(R.string.clip_guard_copied)
        refreshPreview()
        startCountdown()
    }

    /** 立即清除剪贴板。 */
    fun clearNow() {
        clearJob?.cancel()
        _countdownMs.value = 0
        selfClearing = true
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("", "")) }
        _clipPreview.value = ""
        _lastEvent.value = appContext.getString(R.string.clip_guard_cleared)
    }

    private fun startCountdown() {
        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            val total = _delaySec.value * 1000L
            _countdownMs.value = total
            while (_countdownMs.value > 0) {
                delay(100)
                _countdownMs.value = (_countdownMs.value - 100).coerceAtLeast(0)
            }
            selfClearing = true
            runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("", "")) }
            _clipPreview.value = ""
            _lastEvent.value = appContext.getString(R.string.clip_guard_auto_cleared)
        }
    }

    override fun onCleared() {
        listener?.let { clipboard.removePrimaryClipChangedListener(it) }
        clearJob?.cancel()
    }
}
