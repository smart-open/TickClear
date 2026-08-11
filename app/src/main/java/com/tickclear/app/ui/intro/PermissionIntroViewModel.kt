package com.tickclear.app.ui.intro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.permission.PermissionStatus
import com.tickclear.app.domain.repository.PermissionIntroRepository
import com.tickclear.app.domain.scheduler.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「首次启动权限引导」与「设置 → 高级 → 权限配置」复访页的共享 ViewModel。
 *
 * 设计要点：
 * - **现场扫描，不缓存**：每次 [refresh] 重新调 [PermissionChecker] 扫描，避免与
 *   系统真实状态漂移（用户可能中途去系统设置开关权限）。
 * - **`Eagerly` 收集 introDone**：引导完成状态是首次启动时唯一判断依据，
 *   必须立即可用，不能等订阅者挂上来才查询（避免主屏闪一下引导页）。
 *
 * 引导页与复访页共用同一 ViewModel（不同 NavBackStackEntry 实例），但因为 Hilt 路由
 * scope 不同，不会互相覆盖状态；UI 层在 onResume 时主动调 [refresh] 即可保持最新。
 */
@HiltViewModel
class PermissionIntroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val introRepository: PermissionIntroRepository,
) : ViewModel() {

    /** 当前权限状态快照（现场扫描，每次 refresh 更新）。 */
    private val _status = MutableStateFlow(scanStatus())
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    /** 引导完成标志（首次安装 false；用户完成/跳过/手动标记后 true）。 */
    val introDone: StateFlow<Boolean> = introRepository.introDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refresh()
    }

    /** 重新扫描权限状态（onResume 调，或用户从系统设置返回时调）。 */
    fun refresh() {
        _status.value = scanStatus()
    }

    /** 标记引导完成（用户点「完成」或「跳过」时调）。 */
    fun markDone() {
        viewModelScope.launch { introRepository.setIntroDone(true) }
    }

    /** 重置引导标志（用户在复访页点「重新跑引导」时调）。 */
    fun reset() {
        viewModelScope.launch {
            introRepository.setIntroDone(false)
            _status.value = scanStatus()
        }
    }

    /** 现场扫描一次完整状态。 */
    private fun scanStatus(): PermissionStatus = PermissionStatus(
        notification = PermissionChecker.isNotificationGranted(context),
        exactAlarm = PermissionChecker.canScheduleExactAlarms(context),
        fullScreenIntent = PermissionChecker.canUseFullScreenIntent(context),
        batteryOptimization = PermissionChecker.isIgnoringBatteryOptimizations(context),
        drawOverlays = PermissionChecker.canDrawOverlays(context),
        location = PermissionChecker.isLocationGranted(context),
        microphone = PermissionChecker.isMicrophoneGranted(context),
        camera = PermissionChecker.isCameraGranted(context),
    )
}