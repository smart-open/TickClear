package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
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
import java.util.UUID
import javax.inject.Inject

/**
 * 到站提醒（V2.9++）：管理站点列表、监测开关、当前位置获取。
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

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

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
        val st = ArrivalStation(UUID.randomUUID().toString(), clean, lat, lng, radius.coerceIn(50, 2000))
        persist(_stations.value + st)
    }

    fun removeStation(id: String) {
        persist(_stations.value.filter { it.id != id })
    }

    private fun persist(list: List<ArrivalStation>) {
        viewModelScope.launch { settingsRepository.setArrivalStations(encodeStations(list)) }
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
                if (loc != null) _currentLocation.value = loc.latitude to loc.longitude
                else _error.tryEmit(appContext.getString(R.string.arrival_location_unavailable))
            }.onFailure {
                _error.tryEmit(appContext.getString(R.string.arrival_location_unavailable))
            }
        }
    }

    private fun startService() {
        if (_stations.value.isEmpty()) {
            _error.tryEmit(appContext.getString(R.string.arrival_empty))
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

    private fun stopService() {
        runCatching { appContext.stopService(Intent(appContext, ArrivalReminderService::class.java)) }
    }
}
