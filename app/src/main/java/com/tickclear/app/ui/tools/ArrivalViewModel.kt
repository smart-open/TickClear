package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.scheduler.ArrivalReminderService
import com.tickclear.app.domain.tools.ArrivalStation
import com.tickclear.app.domain.tools.decodeStations
import com.tickclear.app.domain.tools.encodeStations
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
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** 一次当前定位结果（含反查到的地点名，供默认填入站点名称）。 */
data class CurrentLoc(val lat: Double, val lng: Double, val placeName: String?)

/** 地点名搜索建议（前向地理编码结果）。 */
data class PlaceSuggestion(val name: String, val lat: Double, val lng: Double, val snippet: String)

/**
 * 到站提醒（V2.9++）：管理站点列表、监测开关、当前位置获取与地点名搜索。
 * 监测开启后启动 [ArrivalReminderService]（前台定位轮询），离开页面不停止服务（独立运行）。
 */
@HiltViewModel
class ArrivalViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _stations = MutableStateFlow<List<ArrivalStation>>(emptyList())
    val stations: StateFlow<List<ArrivalStation>> = _stations.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _currentLocation = MutableStateFlow<CurrentLoc?>(null)
    val currentLocation: StateFlow<CurrentLoc?> = _currentLocation.asStateFlow()

    private val _places = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val places: StateFlow<List<PlaceSuggestion>> = _places.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _error.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.arrivalStations.collect { _stations.value = decodeStations(it) }
        }
        viewModelScope.launch {
            settingsRepository.arrivalEnabled.collect { _enabled.value = it }
        }
    }

    fun addStation(name: String, lat: Double, lng: Double, radius: Int) {
        val clean = name.replace("|", " ").trim()
        if (clean.isEmpty()) { _error.tryEmit(appContext.getString(R.string.arrival_name_required)); return }
        if (lat == 0.0 && lng == 0.0) { _error.tryEmit(appContext.getString(R.string.arrival_coord_required)); return }
        val st = ArrivalStation(UUID.randomUUID().toString(), clean, lat, lng, radius.coerceIn(50, 2000), enabled = true)
        persist(_stations.value + st, restartIfEnabled = _enabled.value)
    }

    fun removeStation(id: String) {
        val next = _stations.value.filter { it.id != id }
        if (next.isEmpty()) {
            // 清空后自动关闭监测，避免「开关仍开但服务已无站点」的状态不一致。
            if (_enabled.value) setEnabled(false)
            persist(next)
        } else {
            persist(next, restartIfEnabled = _enabled.value)
        }
    }

    /** 单独开关某个站点；关闭后服务不再检查该站。 */
    fun setStationEnabled(id: String, on: Boolean) {
        val next = _stations.value.map { if (it.id == id) it.copy(enabled = on) else it }
        persist(next, restartIfEnabled = _enabled.value)
    }

    /**
     * 地点名前向搜索（地理编码）：输入名称返回候选地点列表，供下拉选择直接填入名称与经纬度。
     * 设备无地理编码后端时静默返回空列表，不影响手动输入。
     */
    fun searchPlaces(query: String) {
        if (query.isBlank()) { _places.value = emptyList(); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Geocoder.isPresent()) {
                    _places.value = emptyList()
                    return@launch
                }
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val list = geocoder.getFromLocationName(query, 5) ?: emptyList()
                _places.value = list.mapNotNull { addr -> toSuggestion(addr) }
            }.onFailure {
                _places.value = emptyList()
            }
        }
    }

    private fun toSuggestion(addr: Address): PlaceSuggestion? {
        val lat = addr.latitude
        val lng = addr.longitude
        if (lat == 0.0 && lng == 0.0) return null
        val name = buildPlaceName(addr) ?: return null
        return PlaceSuggestion(name, lat, lng, addr.getAddressLine(0) ?: "")
    }

    private fun buildPlaceName(addr: Address): String? {
        val candidates = listOfNotNull(addr.featureName, addr.locality, addr.subLocality, addr.adminArea)
            .filter { it.isNotBlank() }
        val first = candidates.firstOrNull() ?: addr.getAddressLine(0)
        return first?.takeIf { it.isNotBlank() }
    }

    private fun persist(list: List<ArrivalStation>, restartIfEnabled: Boolean = false) {
        viewModelScope.launch {
            settingsRepository.setArrivalStations(encodeStations(list))
            // 监测开启时，重新触发服务 onStartCommand 以重新加载站点列表（已有实例复用监听）。
            if (restartIfEnabled && _enabled.value) restartService()
        }
    }

    private fun restartService() {
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, ArrivalReminderService::class.java))
        }.onFailure { AppLogger.e("ArrivalVM", "restart service failed", it) }
    }

    fun setEnabled(on: Boolean) {
        viewModelScope.launch { settingsRepository.setArrivalEnabled(on) }
        if (on) startService() else stopService()
    }

    fun fetchCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                _error.tryEmit(appContext.getString(R.string.arrival_permission_required))
                return@launch
            }
            runCatching {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    val placeName = reverseGeocode(loc.latitude, loc.longitude)
                    _currentLocation.value = CurrentLoc(loc.latitude, loc.longitude, placeName)
                } else {
                    _error.tryEmit(appContext.getString(R.string.arrival_location_unavailable))
                }
            }.onFailure {
                _error.tryEmit(appContext.getString(R.string.arrival_location_unavailable))
            }
        }
    }

    /** 反查当前坐标的地点名，失败返回 null（不影响定位本身）。 */
    private fun reverseGeocode(lat: Double, lng: Double): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val list = geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
        list.firstOrNull()?.let { buildPlaceName(it) }
    }.getOrNull()

    private fun startService() {
        if (_stations.value.isEmpty()) {
            _error.tryEmit(appContext.getString(R.string.arrival_empty))
            viewModelScope.launch { settingsRepository.setArrivalEnabled(false) }
            return
        }
        if (!hasLocationPermission()) {
            _error.tryEmit(appContext.getString(R.string.arrival_permission_required))
            viewModelScope.launch { settingsRepository.setArrivalEnabled(false) }
            return
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, ArrivalReminderService::class.java))
        }.onFailure {
            AppLogger.e("ArrivalVM", "start service failed", it)
            _error.tryEmit(appContext.getString(R.string.arrival_start_fail))
            viewModelScope.launch { settingsRepository.setArrivalEnabled(false) }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun stopService() {
        runCatching { appContext.stopService(Intent(appContext, ArrivalReminderService::class.java)) }
    }
}
