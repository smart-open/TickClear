package com.tickclear.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tickclear.app.domain.scheduler.ReminderReceiver
import com.tickclear.app.domain.scheduler.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全屏提醒展示页（由高优先级通知的 full-screen intent 拉起）。
 * 提供「完成 / 稍后 / 关闭」三个动作。Android 14+ 仅在应用前台或系统允许时真正全屏弹出，
 * 否则降级为普通通知（符合系统隐私策略，非缺陷）。
 */
@AndroidEntryPoint
class FullScreenAlertActivity : ComponentActivity() {
    private var title by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // V2.8X 修复：此前既没有清单属性也没有运行时调用 —— 锁屏态下全屏意图被系统拉起后
        // 只能停在锁屏后面，用户看不到提醒页（"锁屏不提醒"的关键一环）。
        // API 27+ 用官方 API；24~26 退回等价的窗口标志（该场景下这些 flag 仍受支持）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        // 解除 keyguard（无密码锁屏可直接解除；有密码锁屏系统会保留验证，属预期行为）。
        // requestDismissKeyguard 需 API 26+；24~25 走上方窗口标志路径，此处跳过。
        runCatching {
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        }

        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: run { finish(); return }
        val instanceId = intent.getStringExtra(ReminderReceiver.EXTRA_INSTANCE_ID) ?: "$taskId@today"

        var snoozeMin by mutableIntStateOf(ReminderReceiver.SNOOZE_DEFAULT_MIN)
        // 提醒级别：稍后提醒要沿用同一闹钟通道，否则高优先级会被降级成可被 Doze 延迟的普通闹钟。
        var level = "high"
        lifecycleScope.launch {
            val ep = EntryPointAccessors.fromApplication(this@FullScreenAlertActivity, ReminderScheduler.ReminderEntryPoint::class.java)
            val task = ep.taskRepository().getById(taskId)
            title = task?.title ?: getString(R.string.fullscreen_reminder_unknown)
            level = task?.reminderLevel ?: "high"
            // V2.30 稍后提醒时长取用户设置（默认 15 分钟）。
            snoozeMin = ep.settingsRepository().snoozeDefaultMin.first()
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = getString(R.string.fullscreen_reminder_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        TextButton(onClick = {
                            dismissNotification(instanceId)
                            finish()
                        }) {
                            Text(getString(R.string.fullscreen_reminder_dismiss))
                        }
                        TextButton(onClick = {
                            ReminderScheduler.scheduleSnooze(this@FullScreenAlertActivity, instanceId, taskId, snoozeMin, level)
                            dismissNotification(instanceId)
                            finish()
                        }) {
                            Text(getString(R.string.fullscreen_reminder_snooze))
                        }
                        Button(onClick = {
                            // 修复：此前 launch 后立即 finish()，lifecycleScope 随 onDestroy 取消，
                            // 完成写库可能被中途取消（"点了完成却没生效"）。改为写完再关闭页面。
                            lifecycleScope.launch {
                                complete(taskId, instanceId)
                                dismissNotification(instanceId)
                                finish()
                            }
                        }) {
                            Text(getString(R.string.fullscreen_reminder_complete))
                        }
                    }
                }
            }
        }
    }

    /** 收起通知栏里对应的那条提醒：全屏页处理完动作后通知不应继续悬挂。 */
    private fun dismissNotification(instanceId: String) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(com.tickclear.app.domain.scheduler.ReminderIds.notificationId(instanceId))
        }
    }

    private suspend fun complete(taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(this, ReminderScheduler.ReminderEntryPoint::class.java)
        val task = ep.taskRepository().getActiveById(taskId) ?: ep.taskRepository().getById(taskId) ?: return
        ep.completeTaskUseCase()(task, instanceId)
    }
}
