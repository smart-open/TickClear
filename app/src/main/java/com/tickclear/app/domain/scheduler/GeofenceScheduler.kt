package com.tickclear.app.domain.scheduler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 位置提醒调度（V2.13）：生命周期管理器，按是否存在「启用且含经纬度」的任务
 * 启停 [LocationReminderService]（前台主动轮询），替代原系统 addProximityAlert。
 * 零 GMS 依赖；未授予精确定位权限时不启动服务。
 */
@Singleton
class GeofenceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 实例方法：用注入的上下文同步服务状态（供任务保存后调用）。 */
    fun sync() = sync(context)

    /** 注册/注销单个任务：均触发一次状态同步（启/停服务）。参数保留以兼容调用方。 */
    fun register(@Suppress("UNUSED_PARAMETER") task: Task) = sync(context)
    fun unregister(@Suppress("UNUSED_PARAMETER") taskId: String) = sync(context)

    companion object {
        private fun hasGeoTasks(context: Context): Boolean = runCatching {
            val ep = EntryPointAccessors.fromApplication(context, LocationReminderService.LocationReminderEntryPoint::class.java)
            runBlocking(Dispatchers.IO) {
                ep.taskRepository().observeAll().first()
            }.any { it.isEnabled() && it.geoLat != null && it.geoLng != null && it.geoRadius != null }
        }.getOrDefault(false)

        /** 同步位置提醒服务：有任务且已授权则前台启动，否则停止。供 BootReceiver / 任务保存后调用。 */
        fun sync(context: Context) {
            val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val intent = Intent(context, LocationReminderService::class.java)
            if (permitted && hasGeoTasks(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
