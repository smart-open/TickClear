package com.tickclear.app.domain.scheduler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 位置提醒调度（V2.13）：生命周期管理器，按是否存在「启用且含经纬度」的任务
 * 启停 [LocationReminderService]（前台主动轮询），替代原系统 addProximityAlert。
 * 零 GMS 依赖；未授予精确定位权限时不启动服务。
 */
@Singleton
class GeofenceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // 内部作用域：register/unregister/sync 为 fire-and-forget，避免阻塞调用方
    // （ViewModel 主线程 / 任务保存路径）。@Singleton 生命周期内偶发启动短任务，作用域随之回收。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 实例方法：用注入的上下文异步同步服务状态（供任务保存后调用）。 */
    fun sync() = scope.launch { syncNow(context) }

    /** 注册/注销单个任务：均触发一次状态同步（启/停服务）。参数保留以兼容调用方。 */
    fun register(@Suppress("UNUSED_PARAMETER") task: Task) = scope.launch { syncNow(context) }
    fun unregister(@Suppress("UNUSED_PARAMETER") taskId: String) = scope.launch { syncNow(context) }

    companion object {
        private suspend fun hasGeoTasks(context: Context): Boolean = runCatching {
            val ep = EntryPointAccessors.fromApplication(context, LocationReminderService.LocationReminderEntryPoint::class.java)
            withContext(Dispatchers.IO) {
                ep.taskRepository().observeAll().first()
            }.any { it.isEnabled() && it.geoLat != null && it.geoLng != null && it.geoRadius != null }
        }.getOrDefault(false)

        /** 同步位置提醒服务：有任务且已授权则前台启动，否则停止。供 BootReceiver / 任务保存后调用。 */
        private suspend fun syncNow(context: Context) {
            val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val intent = Intent(context, LocationReminderService::class.java)
            if (permitted && hasGeoTasks(context)) {
                // 用户保存含位置提醒的任务后立刻切后台，协程恢复时进程已处于后台，
                // Android 12+ 会抛 ForegroundServiceStartNotAllowedException（且无 try/catch 的协程里直接崩溃）。
                // 兜底捕获，避免崩溃；失败时由下次进入前台/重启的 rescheduleAll 重建。
                runCatching {
                    context.startForegroundService(intent)
                }.onFailure { AppLogger.w("GeofenceScheduler", "startForegroundService 失败（可能进程在后台）：${it.message}") }
            } else {
                context.stopService(intent)
            }
        }

        /** 供 BootReceiver 在协程内调用（重启后重建位置提醒服务）。 */
        suspend fun sync(context: Context) = syncNow(context)
    }
}
