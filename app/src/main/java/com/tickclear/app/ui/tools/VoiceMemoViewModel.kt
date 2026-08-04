package com.tickclear.app.ui.tools

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.data.local.entities.VoiceMemoEntity
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.repository.VoiceMemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * 语音备忘录（V2.9）：录制 / 播放 / 删除，音频文件存于 filesDir/voice_memos/。
 * 麦克风权限由 Composable 经 accompanist 申请；本 VM 假定已授权再 startRecording。
 * MediaRecorder / MediaPlayer 的 prepare/start/stop 跑在 IO 线程，避免主线程阻塞。
 */
@HiltViewModel
class VoiceMemoViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: VoiceMemoRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val dir = File(appContext.filesDir, "voice_memos").apply { mkdirs() }

    private val _memos = MutableStateFlow<List<VoiceMemoEntity>>(emptyList())
    val memos: StateFlow<List<VoiceMemoEntity>> = _memos.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordElapsedMs = MutableStateFlow(0L)
    val recordElapsedMs: StateFlow<Long> = _recordElapsedMs.asStateFlow()

    private val _recordTitle = MutableStateFlow("")
    val recordTitle: StateFlow<String> = _recordTitle.asStateFlow()

    /** 当前正在播放/暂停的备忘录 id（null 表示无活动播放器）。 */
    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    /** 录音降噪开关（持久化于 DataStore，默认关闭）。开启后用 VOICE_RECOGNITION 音源弱化环境杂音。 */
    private val _noiseReduction = MutableStateFlow(false)
    val noiseReduction: StateFlow<Boolean> = _noiseReduction.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playPositionMs = MutableStateFlow(0L)
    val playPositionMs: StateFlow<Long> = _playPositionMs.asStateFlow()

    private val _playDurationMs = MutableStateFlow(0L)
    val playDurationMs: StateFlow<Long> = _playDurationMs.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _error.asSharedFlow()

    private val isRecordingGuard = AtomicBoolean(false)
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var recordStartMs = 0L
    private var player: MediaPlayer? = null
    private var recordTickerJob: Job? = null
    private var playTickerJob: Job? = null

    init {
        repo.observeAll()
            .onEach { _memos.value = it }
            .launchIn(viewModelScope)
        settings.voiceNoiseReduction
            .onEach { _noiseReduction.value = it }
            .launchIn(viewModelScope)
    }

    /** 设置录音降噪开关（同步持久化）。 */
    fun setNoiseReduction(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { settings.setVoiceNoiseReduction(enabled) }
            _noiseReduction.value = enabled
        }
    }

    fun onTitleChange(value: String) {
        _recordTitle.value = value
    }

    // ---------------- 录制 ----------------

    fun startRecording() {
        if (!isRecordingGuard.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(dir, "memo_${System.currentTimeMillis()}.m4a")
                val r = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }).apply {
                    // 降噪开启：使用 VOICE_RECOGNITION 音源，触发平台级降噪/回声消除（无新依赖）；
                    // 关闭：标准 MIC 音源。
                    setAudioSource(
                        if (_noiseReduction.value) {
                            MediaRecorder.AudioSource.VOICE_RECOGNITION
                        } else {
                            MediaRecorder.AudioSource.MIC
                        },
                    )
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file)
                }
                r.prepare()
                r.start()
                recorder = r
                currentFile = file
                recordStartMs = System.currentTimeMillis()
                _recordElapsedMs.value = 0
                _isRecording.value = true
                startRecordTicker()
            } catch (e: Exception) {
                AppLogger.e("VoiceMemoVM", "startRecording failed", e)
                _error.tryEmit(appContext.getString(R.string.voice_permission_required))
                _isRecording.value = false
                isRecordingGuard.set(false)
            }
        }
    }

    /** 停止并保存（save=true）或放弃（save=false）。 */
    fun stopRecording(save: Boolean) {
        if (!_isRecording.value) {
            isRecordingGuard.set(false)
            return
        }
        val r = recorder
        val file = currentFile
        if (r == null || file == null) {
            _isRecording.value = false
            _recordElapsedMs.value = 0
            _recordTitle.value = ""
            isRecordingGuard.set(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val durationMs = System.currentTimeMillis() - recordStartMs
            recordTickerJob?.cancel()
            recordTickerJob = null
            runCatching { r.stop() }.onFailure { e -> AppLogger.e("VoiceMemoVM", "recorder.stop failed", e) }
            runCatching { r.release() }
            recorder = null
            currentFile = null
            _isRecording.value = false
            _recordElapsedMs.value = 0

            if (save && file.exists() && file.length() > 0) {
                val entity = VoiceMemoEntity(
                    title = _recordTitle.value.trim(),
                    filePath = file.absolutePath,
                    durationMs = durationMs,
                    createdAt = System.currentTimeMillis(),
                )
                runCatching { repo.insert(entity) }
                    .onFailure { e ->
                        AppLogger.e("VoiceMemoVM", "insert memo failed", e)
                        file.delete()
                        _error.tryEmit(appContext.getString(R.string.err_unknown))
                    }
            } else {
                file.delete()
            }
            _recordTitle.value = ""
            isRecordingGuard.set(false)
        }
    }

    private fun startRecordTicker() {
        recordTickerJob?.cancel()
        recordTickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(500)
                _recordElapsedMs.value = System.currentTimeMillis() - recordStartMs
            }
        }
    }

    // ---------------- 播放 ----------------

    fun togglePlay(memo: VoiceMemoEntity) {
        if (_activeId.value == memo.id) {
            if (_isPlaying.value) pausePlayback() else resumePlayback()
            return
        }
        stopPlaybackInternal()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(memo.filePath)
                    prepare()
                }
                player = mp
                _activeId.value = memo.id
                _playDurationMs.value = mp.duration.toLong()
                _playPositionMs.value = 0
                mp.setOnCompletionListener {
                    runCatching { it.release() }
                    player = null
                    _activeId.value = null
                    _isPlaying.value = false
                    _playPositionMs.value = 0
                    playTickerJob?.cancel()
                }
                mp.start()
                _isPlaying.value = true
                startPlayTicker(mp)
            } catch (e: Exception) {
                AppLogger.e("VoiceMemoVM", "play failed: ${memo.filePath}", e)
                _error.tryEmit(appContext.getString(R.string.err_unknown))
                _activeId.value = null
            }
        }
    }

    private fun pausePlayback() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        _playPositionMs.value = mp.currentPosition.toLong()
        _isPlaying.value = false
        playTickerJob?.cancel()
    }

    private fun resumePlayback() {
        val mp = player ?: return
        mp.seekTo(_playPositionMs.value.toInt())
        mp.start()
        _isPlaying.value = true
        startPlayTicker(mp)
    }

    private fun startPlayTicker(mp: MediaPlayer) {
        playTickerJob?.cancel()
        playTickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (mp.isPlaying) {
                delay(250)
                _playPositionMs.value = mp.currentPosition.toLong()
            }
        }
    }

    private fun stopPlaybackInternal() {
        playTickerJob?.cancel()
        playTickerJob = null
        runCatching { player?.release() }
        player = null
        _activeId.value = null
        _isPlaying.value = false
        _playPositionMs.value = 0
        _playDurationMs.value = 0
    }

    fun deleteMemo(memo: VoiceMemoEntity) {
        if (_activeId.value == memo.id) stopPlaybackInternal()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.deleteById(memo.id) }
            runCatching { File(memo.filePath).delete() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordTickerJob?.cancel()
        playTickerJob?.cancel()
        runCatching { recorder?.release() }
        recorder = null
        runCatching { player?.release() }
        player = null
    }
}
