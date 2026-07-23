package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

/**
 * 地理围栏（邻近告警）接收器：进入围栏半径时触发。
 * 进入后复用 [ReminderReceiver] 的提醒展示逻辑（以 SHOW 广播触发，含渠道/优先级/静音判定）。
 * 仅「进入」触发，离开不重复提醒。
 */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val appCtx = context?.applicationContext ?: return
        if (intent?.action != GeofenceScheduler.ACTION_GEOFENCE) return
        // 仅进入围栏时触发（KEY_PROXIMITY_ENTERING == true）
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        if (!entering) return
        val taskId = intent.getStringExtra(GeofenceScheduler.EXTRA_TASK_ID) ?: return

        val show = Intent(appCtx, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_INSTANCE_ID, "$taskId@geo")
        }
        appCtx.sendBroadcast(show)
    }
}
