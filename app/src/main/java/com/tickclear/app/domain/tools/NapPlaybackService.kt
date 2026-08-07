package com.tickclear.app.domain.tools

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.scheduler.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 午休小憩白噪音前台播放服务（V2.9++）。
 *
 * 为何需要前台服务：小憩 20–90 分钟，用户必定熄屏并把 App 退到后台；
 * 若仅在 Activity 内播放，进程被回收后声音即中断。改用 [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK]
 * 前台服务，系统不会将其纳入 Doze 限制，可稳定跨熄屏循环播放本地白噪音（NoiseSynth 为 AudioTrack 静态循环，CPU 占用极低）。
 *
 * 渐隐（fade）：用户可选的「最后 N 分钟渐隐」——前段满音量助眠，临近结束时把音量从 1 平滑降到 0 后自停，
 * 避免声音整夜空放、也更省电。fadeMin=0 表示全程播放，至唤醒时刻自动停止。
 */
class NapPlaybackService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    @android.annotation.SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY

        val scene = intent?.getStringExtra(EXTRA_SCENE) ?: "rain"
        val durationMin = (intent?.getIntExtra(EXTRA_DURATION_MIN, 30) ?: 30).coerceAtLeast(1)
        val fadeMin = (intent?.getIntExtra(EXTRA_FADE_MIN, 0) ?: 0).coerceAtLeast(0)
        val endAt = intent?.getLongExtra(EXTRA_END_AT, System.currentTimeMillis() + durationMin * 60_000L)
            ?: System.currentTimeMillis() + durationMin * 60_000L

        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(scene),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification(scene))
        }

        NoiseSynth.play(scene, 1f)
        scope.launch { runPlayback(scene, durationMin, fadeMin, endAt) }
        // 非 sticky：被系统回收后不自动重启（小憩是临时行为，闹钟仍会按时唤醒）。
        return START_NOT_STICKY
    }

    private suspend fun runPlayback(scene: String, durationMin: Int, fadeMin: Int, endAt: Long) {
        val fadeMs = min(fadeMin, durationMin).coerceAtLeast(0) * 60_000L
        if (fadeMs <= 0L) {
            // 全程满音量，至唤醒时刻自动停止。
            val waitMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(waitMs)
            stopSelf()
            return
        }
        // 前段满音量，最后 fadeMs 渐隐至 0。
        val fadeStart = endAt - fadeMs
        var wait = (fadeStart - System.currentTimeMillis()).coerceAtLeast(0L)
        while (wait > 0) {
            delay(min(wait, 1000L))
            wait -= 1000L
        }
        val rampStart = System.currentTimeMillis()
        while (true) {
            val t = ((System.currentTimeMillis() - rampStart).toFloat() / fadeMs).coerceIn(0f, 1f)
            NoiseSynth.setVolume(1f - t)
            if (t >= 1f) {
                delay(200)
                stopSelf()
                return
            }
            delay(1000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { NoiseSynth.stop() }
        super.onDestroy()
    }

    private fun buildNotification(scene: String): Notification {
        val sceneLabel = when (scene) {
            "cafe" -> getString(R.string.white_noise_cafe)
            "stream" -> getString(R.string.white_noise_stream)
            else -> getString(R.string.white_noise_rain)
        }
        val stopIntent = Intent(this, NapPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this,
            NOTIF_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val launch = Intent(this, com.tickclear.app.MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            this,
            NOTIF_ID + 1,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_NAP_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.nap_playback_title))
            .setContentText(getString(R.string.nap_playback_text, sceneLabel))
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.nap_playback_stop),
                stopPi,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIF_ID = 9302
        const val ACTION_STOP = "com.tickclear.app.action.NAP_PLAYBACK_STOP"
        const val EXTRA_SCENE = "nap_scene"
        const val EXTRA_DURATION_MIN = "nap_duration_min"
        const val EXTRA_FADE_MIN = "nap_fade_min"
        const val EXTRA_END_AT = "nap_end_at"

        /** 供 NapReceiver 在唤醒时停止播放服务。 */
        fun stopIntent(context: Context): Intent =
            Intent(context, NapPlaybackService::class.java).apply { action = ACTION_STOP }
    }
}
