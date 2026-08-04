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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
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
 * 位置提醒前台服务（V2.13）：以主动轮询 [LocationManager] 替代系统 [LocationManager.addProximityAlert]，
 * 对厂商省电策略更友好（addProximityAlert 在部分 ROM 上后台长期静默失效）。
 * - 零 GMS 依赖，仅用 android.location；
 * - 仅当存在启用且含经纬度的任务时由 [GeofenceScheduler] 启动；进入半径即经 [ReminderReceiver] 复用提醒通知；
 * - 持续运行会占用定位资源，故无位置提醒任务时立即停止（见 [GeofenceScheduler]）。
 */
class LocationReminderService : Service() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocationReminderEntryPoint {
        fun taskRepository(): TaskRepository
    }

    private lateinit var locationManager: LocationManager
    private var handlerThread: HandlerThread? = null
    private var listener: LocationListener? = null
    private val inside = mutableSetOf<String>()
    private var geoTasks: List<Task> = emptyList()
    // 服务作用域：查询在 IO 线程执行，避免 onStartCommand（主线程）被 runBlocking 阻塞引发 ANR。
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 无定位权限时无法以 location 类型前台服务运行（Android 14 会抛 SecurityException），
        // 直接停止并返回 START_NOT_STICKY，避免 START_STICKY 重启陷入崩溃循环。
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 三参 startForeground(类型) 自 API 29(Q) 才有；低于 Q 无前台服务类型概念，退回两参。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        serviceScope.launch {
            loadTasks()
            if (geoTasks.isEmpty()) {
                stopSelf()
            } else {
                startPolling()
            }
        }
        // 进程被回收后重建：若仍有位置提醒任务则系统重启服务（START_STICKY）。
        return START_STICKY
    }

    private suspend fun loadTasks() {
        val ep = EntryPointAccessors.fromApplication(this, LocationReminderEntryPoint::class.java)
        geoTasks = runCatching {
            ep.taskRepository().observeAll().first()
        }.getOrDefault(emptyList())
            .filter { it.isEnabled() && it.geoLat != null && it.geoLng != null && it.geoRadius != null }
    }

    private fun startPolling() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        // V2.8X 修复：onStartCommand 会被反复调用（新增位置任务、START_STICKY 重启、GeofenceScheduler.sync）。
        // 此前每次都新建 HandlerThread 并重新注册 LocationListener，旧的既不反注册也不 quit ——
        // 线程与监听器逐次堆积（onDestroy 只清理最后一份），既泄露线程也持续多路耗电定位。
        // 已在监听中则直接复用；任务列表变化由 geoTasks 字段承载，无需重建监听。
        if (listener != null && handlerThread != null) return
        handlerThread = HandlerThread("LocationReminder").also { it.start() }
        val looper = handlerThread!!.looper
        val locListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) = checkProximity(loc)
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = locListener
        val providers: List<String> = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        for (provider in providers) {
            if (runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching {
                    locationManager.requestLocationUpdates(provider, POLL_INTERVAL_MS, POLL_MIN_DISTANCE_M, locListener, looper)
                }
            }
        }
        // 立即用上次已知位置做一次判定，提升首次命中速度。
        val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        last?.let { checkProximity(it) }
    }

    /** 对每个位置任务计算与当前位置距离，进入半径触发一次、离开半径复位。 */
    private fun checkProximity(loc: Location) {
        for (task in geoTasks) {
            val lat = task.geoLat ?: continue
            val lng = task.geoLng ?: continue
            val radius = (task.geoRadius ?: 100).toDouble()
            val dist = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, dist)
            val within = dist[0] <= radius
            val id = task.id
            if (within && inside.add(id)) {
                fireReminder(id)
            } else if (!within && inside.contains(id)) {
                inside.remove(id)
            }
        }
    }

    private fun fireReminder(taskId: String) {
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_INSTANCE_ID, "$taskId@geo")
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.location_reminder_service_title))
            .setContentText(getString(R.string.location_reminder_service_text))
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
        const val NOTIF_ID = 2001
        const val POLL_INTERVAL_MS = 15_000L
        const val POLL_MIN_DISTANCE_M = 0f
    }
}
