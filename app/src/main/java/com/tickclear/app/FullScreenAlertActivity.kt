package com.tickclear.app

import android.os.Bundle
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
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: run { finish(); return }
        val instanceId = intent.getStringExtra(ReminderReceiver.EXTRA_INSTANCE_ID) ?: "$taskId@today"

        var snoozeMin by mutableStateOf(ReminderReceiver.SNOOZE_DEFAULT_MIN)
        lifecycleScope.launch {
            val ep = EntryPointAccessors.fromApplication(this@FullScreenAlertActivity, ReminderScheduler.ReminderEntryPoint::class.java)
            title = ep.taskRepository().getById(taskId)?.title ?: getString(R.string.fullscreen_reminder_unknown)
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
                        TextButton(onClick = { finish() }) {
                            Text(getString(R.string.fullscreen_reminder_dismiss))
                        }
                        TextButton(onClick = {
                            lifecycleScope.launch {
                                ReminderScheduler.scheduleSnooze(this@FullScreenAlertActivity, instanceId, taskId, snoozeMin)
                            }
                            finish()
                        }) {
                            Text(getString(R.string.fullscreen_reminder_snooze))
                        }
                        Button(onClick = {
                            lifecycleScope.launch { complete(taskId, instanceId) }
                            finish()
                        }) {
                            Text(getString(R.string.fullscreen_reminder_complete))
                        }
                    }
                }
            }
        }
    }

    private suspend fun complete(taskId: String, instanceId: String) {
        val ep = EntryPointAccessors.fromApplication(this, ReminderScheduler.ReminderEntryPoint::class.java)
        val task = ep.taskRepository().getActiveById(taskId) ?: ep.taskRepository().getById(taskId) ?: return
        ep.completeTaskUseCase()(task, instanceId)
    }
}
