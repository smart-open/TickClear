package com.tickclear.app.ui.tools

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 隐私检查（V2.9++，无需 root）：遍历已安装应用，按危险权限组检查「系统是否已授予」对应权限，
 * 分组展示哪些应用持有了短信/通讯录/通话记录/电话/日历/相机/麦克风/位置/存储等隐私权限。
 * 直接读取 PackageManager 的授权标志（REQUESTED_PERMISSION_GRANTED），不依赖 root。
 */
@HiltViewModel
class PrivacyCheckViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class PermGroup(val key: String, val labelRes: Int, val perms: List<String>)
    data class AppInfo(val name: String, val pkg: String)
    data class GroupResult(val label: String, val apps: List<AppInfo>)

    // 权限名用字面量（非 Manifest.permission.* 字段引用），规避 minSdk26 下的 lint NewApi。
    // 分组中文标签抽离到 strings.xml（labelRes），遵守「中文全抽离」红线。
    private val GROUPS = listOf(
        PermGroup("sms", R.string.perm_group_sms, listOf(
            "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS", "android.permission.READ_CELL_BROADCASTS",
        )),
        PermGroup("contacts", R.string.perm_group_contacts, listOf(
            "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.GET_ACCOUNTS",
        )),
        PermGroup("call_log", R.string.perm_group_call_log, listOf(
            "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG", "android.permission.PROCESS_OUTGOING_CALLS",
        )),
        PermGroup("phone", R.string.perm_group_phone, listOf(
            "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS", "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS", "android.permission.ADD_VOICEMAIL", "android.permission.USE_SIP",
            "android.permission.ACCEPT_HANDOVER",
        )),
        PermGroup("calendar", R.string.perm_group_calendar, listOf(
            "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR",
        )),
        PermGroup("camera", R.string.perm_group_camera, listOf("android.permission.CAMERA")),
        PermGroup("microphone", R.string.perm_group_microphone, listOf("android.permission.RECORD_AUDIO")),
        PermGroup("location", R.string.perm_group_location, listOf(
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
        )),
        PermGroup("storage", R.string.perm_group_storage, listOf(
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
        )),
        PermGroup("body_sensors", R.string.perm_group_body_sensors, listOf("android.permission.BODY_SENSORS")),
        PermGroup("nearby", R.string.perm_group_nearby, listOf(
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT",
        )),
        PermGroup("notifications", R.string.perm_group_notifications, listOf("android.permission.POST_NOTIFICATIONS")),
    )

    private val _results = MutableStateFlow<List<GroupResult>>(emptyList())
    val results: StateFlow<List<GroupResult>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _error.asSharedFlow()

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = appContext.packageManager
                val installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                val grantedByPkg = mutableMapOf<String, MutableSet<String>>()
                val labelByPkg = mutableMapOf<String, String>()
                for (pi in installed) {
                    val perms = pi.requestedPermissions ?: continue
                    val flags = pi.requestedPermissionsFlags ?: continue
                    val set = mutableSetOf<String>()
                    for (i in perms.indices) {
                        if ((flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            set.add(perms[i])
                        }
                    }
                    if (set.isNotEmpty()) {
                        grantedByPkg[pi.packageName] = set
                        labelByPkg[pi.packageName] = runCatching {
                            pm.getApplicationLabel(pi.applicationInfo).toString()
                        }.getOrDefault(pi.packageName)
                    }
                }
                val out = mutableListOf<GroupResult>()
                for (g in GROUPS) {
                    val apps = mutableListOf<AppInfo>()
                    for ((pkg, set) in grantedByPkg) {
                        if (g.perms.any { it in set }) {
                            apps.add(AppInfo(labelByPkg[pkg] ?: pkg, pkg))
                        }
                    }
                    apps.sortBy { it.name }
                    if (apps.isNotEmpty()) out.add(GroupResult(appContext.getString(g.labelRes), apps))
                }
                out.sortBy { it.label }
                _results.value = out
            } catch (e: Exception) {
                AppLogger.e("PrivacyCheck", "scan failed", e)
                _error.tryEmit(appContext.getString(R.string.privacy_scan_fail))
            } finally {
                _scanning.value = false
            }
        }
    }
}
