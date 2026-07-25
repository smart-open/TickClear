package com.tickclear.app.domain.assistant

import android.Manifest
import android.app.Notification
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.scheduler.NotificationHelper
import com.tickclear.app.ui.navigation.ShortcutHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 常驻语音唤醒前台服务（V2.66）：在后台持续以系统识别器做关键词 spotting，
 * 命中设置中的唤醒词（默认「小清 / 嘿点清」）即经 [WakeWordBus] 唤起助手并跳转助手页自动收音。
 *
 * 实现复用 [WakeWordManager]（系统 [android.speech.SpeechRecognizer] best-effort，零新依赖）。
 * ⚠️ 局限：依赖系统识别服务，并非神经网络离线模型；真正端侧离线唤醒词需引入 ML 运行时（与「不引入新依赖」红线冲突）。
 */
class WakeWordService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WakeWordEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var manager: WakeWordManager? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        // 无录音权限则无法监听，直接停止（设置页会引导授权）。
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startListening()
        return START_STICKY
    }

    private fun startListening() {
        val settings = EntryPointAccessors
            .fromApplication(this, WakeWordEntryPoint::class.java)
            .settingsRepository()
        val mgr = WakeWordManager(applicationContext, settings)
        if (!mgr.isAvailable) {
            stopSelf()
            return
        }
        manager = mgr
        running = true
        scope.launch {
            // 命中即触发助手；WakeWordManager 单次触发后自停，这里重新拉起持续监听。
            mgr.start { onWake() }
        }
    }

    private fun onWake() {
        WakeWordBus.wake()
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ShortcutHelper.EXTRA_SHORTCUT_ACTION, ShortcutHelper.ACTION_ASSISTANT)
            }
            startActivity(intent)
        }
        // 单次触发后重新进入持续监听，保持「常驻」。
        scope.launch { manager?.start { onWake() } }
    }

    override fun onDestroy() {
        running = false
        manager?.stop()
        manager = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SILENT)
            .setContentTitle(getString(R.string.wake_listening_notification_title))
            .setContentText(getString(R.string.wake_listening_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 9001
    }
}
