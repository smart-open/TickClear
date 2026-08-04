package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.app.AppOpsManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 隐私摄像头/麦克风检测（工具箱「摄像头检测」，V2.9++，简易版）。
 *  - 相机实时占用：CameraManager.AvailabilityCallback（可靠，无需特殊权限）。
 *  - 麦克风占用：AppOpsManager 监听 RECORD_AUDIO op 变化（尽力而为，依赖设备/ROM）。
 *  - 已授权相机/麦克风的应用清单：PackageManager 静态审计，最可靠、最有实用价值。
 * 注意：受 Android 隐私沙箱限制，无法枚举「正在运行且偷偷调用」的第三方应用包名，
 * 本工具以「实时指示灯 + 应用权限审计」帮助用户自查与收敛权限。
 */
@HiltViewModel
class PrivacyDetectViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    @Immutable
    data class DetectEvent(val id: Long, val time: Long, val text: String, val kind: String)

    @Immutable
    data class SensitiveApp(
        val appName: String,
        val packageName: String,
        val hasCamera: Boolean,
        val hasMic: Boolean,
    )

    private val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val pm = appContext.packageManager

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _cameraInUse = MutableStateFlow(false)
    val cameraInUse: StateFlow<Boolean> = _cameraInUse.asStateFlow()

    private val _micInUse = MutableStateFlow(false)
    val micInUse: StateFlow<Boolean> = _micInUse.asStateFlow()

    private val _events = MutableStateFlow<List<DetectEvent>>(emptyList())
    val events: StateFlow<List<DetectEvent>> = _events.asStateFlow()

    private val _appList = MutableStateFlow<List<SensitiveApp>>(emptyList())
    val appList: StateFlow<List<SensitiveApp>> = _appList.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _error.asSharedFlow()

    private var availCb: CameraManager.AvailabilityCallback? = null
    private var opCb: AppOpsManager.OnOpChangedListener? = null
    private var micResetJob: Job? = null
    private var eventSeq = 0L

    fun start() {
        if (_monitoring.value) return
        _monitoring.value = true

        try {
            availCb = object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(cameraId: String) {
                    _cameraInUse.value = false
                    pushEvent("摄像头空闲", "camera")
                }

                override fun onCameraUnavailable(cameraId: String) {
                    _cameraInUse.value = true
                    pushEvent("摄像头被占用（可能有应用正在调用）", "camera")
                }
            }
            cm.registerAvailabilityCallback(availCb!!, null)
        } catch (e: Exception) {
            AppLogger.e("PrivacyDetect", "register camera cb failed", e)
            _error.tryEmit(appContext.getString(R.string.cam_detect_camera_perm))
        }

        try {
            opCb = AppOpsManager.OnOpChangedListener { op, packageName ->
                when (op) {
                    AppOpsManager.OPSTR_CAMERA -> {
                        pushEvent("摄像头调用：${packageName ?: "?"}", "camera")
                    }
                    AppOpsManager.OPSTR_RECORD_AUDIO -> {
                        _micInUse.value = true
                        pushEvent("麦克风调用：${packageName ?: "?"}", "mic")
                        scheduleMicReset()
                    }
                }
            }
            // minSdk 26 仅提供单 op 重载（String），Android 9+ 才有数组重载；分两次注册最稳妥。
            appOps.startWatchingMode(AppOpsManager.OPSTR_CAMERA, null, opCb!!)
            appOps.startWatchingMode(AppOpsManager.OPSTR_RECORD_AUDIO, null, opCb!!)
        } catch (e: Exception) {
            AppLogger.e("PrivacyDetect", "register op cb failed", e)
        }
    }

    private fun scheduleMicReset() {
        micResetJob?.cancel()
        micResetJob = viewModelScope.launch {
            delay(3000)
            _micInUse.value = false
        }
    }

    fun stop() {
        availCb?.let { runCatching { cm.unregisterAvailabilityCallback(it) } }
        availCb = null
        opCb?.let { runCatching { appOps.stopWatchingMode(it) } }
        opCb = null
        micResetJob?.cancel()
        _monitoring.value = false
        _cameraInUse.value = false
        _micInUse.value = false
    }

    /** 静态审计：列出所有申请了相机或麦克风权限的已安装应用。 */
    fun scanApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SensitiveApp>()
            runCatching {
                val installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                for (pi in installed) {
                    val perms = pi.requestedPermissions ?: continue
                    var hasCam = false
                    var hasMic = false
                    for (p in perms) {
                        if (p == Manifest.permission.CAMERA) hasCam = true
                        if (p == Manifest.permission.RECORD_AUDIO) hasMic = true
                    }
                    if (hasCam || hasMic) {
                        val label = runCatching {
                            pm.getApplicationLabel(pi.applicationInfo).toString()
                        }.getOrDefault(pi.packageName)
                        list.add(SensitiveApp(label, pi.packageName, hasCam, hasMic))
                    }
                }
            }.onFailure { e ->
                AppLogger.e("PrivacyDetect", "scanApps failed", e)
                _error.tryEmit(appContext.getString(R.string.cam_detect_scan_fail))
            }
            list.sortBy { it.appName }
            _appList.value = list
        }
    }

    private fun pushEvent(text: String, kind: String) {
        val ev = DetectEvent(++eventSeq, System.currentTimeMillis(), text, kind)
        _events.value = (_events.value + ev).takeLast(50)
    }

    override fun onCleared() {
        stop()
    }
}
