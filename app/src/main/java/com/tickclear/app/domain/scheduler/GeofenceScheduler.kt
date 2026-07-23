package com.tickclear.app.domain.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.tickclear.app.data.local.entities.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 位置提醒调度：基于系统原生 [LocationManager.addProximityAlert]（无需 Play 服务依赖）。
 * 任务含经纬度与半径时注册邻近告警；进入半径即触发（经 [GeofenceReceiver] → 复用提醒通知）。
 * 注意：邻近告警依赖系统定位，后台触发需定位权限；Android 10+ 后台需 ACCESS_BACKGROUND_LOCATION。
 */
@Singleton
class GeofenceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun register(task: TaskEntity) {
        if (!hasLocation(task)) return
        runCatching {
            locationManager.addProximityAlert(
                task.geoLat!!,
                task.geoLng!!,
                task.geoRadius!!.toFloat(),
                PROXIMITY_EXPIRATION,
                pendingIntent(task.id),
            )
        }
    }

    fun unregister(taskId: String) {
        runCatching { locationManager.removeProximityAlert(pendingIntent(taskId)) }
    }

    private fun hasLocation(task: TaskEntity): Boolean =
        task.geoLat != null && task.geoLng != null && task.geoRadius != null

    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            action = ACTION_GEOFENCE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_GEOFENCE = "com.tickclear.app.geofence.ENTER"
        const val EXTRA_TASK_ID = "task_id"
        const val PROXIMITY_EXPIRATION = -1L // 永不过期（直至任务删除/更新取消）
    }
}
