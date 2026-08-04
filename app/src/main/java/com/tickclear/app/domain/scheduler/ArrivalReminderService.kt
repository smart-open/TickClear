package com.tickclear.app.domain.scheduler

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.tools.ArrivalStation
import com.tickclear.app.domain.tools.decodeStations
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 到站提醒前台服务（V2.9++）：复用 [LocationReminderService] 的主动轮询 [LocationManager] 方案
 * （对厂商省电策略更友好），监测用户保存的站点，进入半径即震动 + 弹通知，避免坐过站。
 * 零 GMS 依赖，仅用 android.location；前台服务类型 location（与 manifest 声明一致）。
 */
class ArrivalReminderService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArrivalEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private lateinit var locationManager: LocationManager
    private var handlerThread: HandlerThread? = null
    private var listener: LocationListener? = null
    private val inside = mutableSetOf<String>()
    private val lastNotified = mutableMapOf<String, Long>()
    private var stations: List<ArrivalStation> = emptyList()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        serviceScope.launch {
            runCatching {
                val text = EntryPointAccessors.fromApplication(this@ArrivalReminderService, ArrivalEntryPoint::class.java)
                    .settingsRepository().arrivalStations.first()
                stations = decodeStations(text)
            }.onFailure { e -> AppLogger.e("Arrival", "load stations failed", e) }
            if (stations.isEmpty()) {
                stopSelf()
            } else {
                startPolling()
            }
        }
        return START_STICKY
    }

    private fun startPolling() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        if (listener != null && handlerThread != null) return
        handlerThread = HandlerThread("ArrivalReminder").also { it.start() }
        val looper = handlerThread!!.looper
        val locListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) = checkProximity(loc)
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = locListener
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching {
                    locationManager.requestLocationUpdates(provider, POLL_INTERVAL_MS, POLL_MIN_DISTANCE_M, locListener, looper)
                }
            }
        }
        val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        last?.let { checkProximity(it) }
    }

    private fun checkProximity(loc: Location) {
        val now = System.currentTimeMillis()
        for (st in stations) {
            val dist = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, st.lat, st.lng, dist)
            val within = dist[0] <= st.radius
            if (within && inside.add(st.id)) {
                // 防抖动：同一站点 60s 内不重复提醒。
                val prev = lastNotified[st.id] ?: 0L
                if (now - prev > 60_000) {
                    lastNotified[st.id] = now
                    fireArrival(st)
                }
            } else if (!within && inside.contains(st.id)) {
                inside.remove(st.id)
            }
        }
    }

    private fun fireArrival(st: ArrivalStation) {
        vibrate()
        NotificationHelper.showArrivalNotification(this, st.name)
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
            }
        }.onFailure { AppLogger.w("Arrival", "vibrate failed", it) }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ARRIVAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.arrival_service_title))
            .setContentText(getString(R.string.arrival_service_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        runCatching { listener?.let { locationManager.removeUpdates(it) } }
        handlerThread?.quitSafely()
        handlerThread = null
        listener = null
        inside.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 2101
        const val POLL_INTERVAL_MS = 10_000L
        const val POLL_MIN_DISTANCE_M = 0f
    }
}
