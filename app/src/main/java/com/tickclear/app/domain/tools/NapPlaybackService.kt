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
 * 渐隐（fade）：用户可选 N 分钟渐隐，播放被切成三段——满音量助眠 → N 分钟渐隐至 0 → **纯静默**。
 * 渐隐刻意在唤醒前 N 分钟就收尾，之后服务自停（释放 AudioTrack、撤下前台通知），
 * 只留 NapScheduler 的一次性唤醒闹钟，既不掩盖唤醒音，也省掉最后一段无谓的音频功耗。
 * fadeMin=0 表示不渐隐，全程满音量播放至唤醒时刻自动停止。
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

        NoiseSynth.playLayer(scene, 1f)
        scope.launch { runPlayback(scene, durationMin, fadeMin, endAt) }
        // 非 sticky：被系统回收后不自动重启（小憩是临时行为，闹钟仍会按时唤醒）。
        return START_NOT_STICKY
    }

    private suspend fun runPlayback(scene: String, durationMin: Int, fadeMin: Int, endAt: Long) {
        val fadeMs = min(fadeMin, durationMin).coerceAtLeast(0) * 60_000L
        if (fadeMs <= 0L) {
            // 不渐隐：全程满音量，至唤醒时刻自动停止。
            delayUntil(endAt)
            stopSelf()
            return
        }
        // 三段式：满音量 → 渐隐 → 纯静默。
        // 渐隐刻意在唤醒前 fadeMs 就结束，留出等长的纯静默段——白噪音一路响到闹钟会掩盖唤醒音、
        // 也让浅睡段被持续声压打扰；提前静音后仅剩唤醒闹钟，睡得更沉、醒得更干净。
        val noiseEndAt = (endAt - fadeMs).coerceAtLeast(System.currentTimeMillis())
        val fadeStart = (noiseEndAt - fadeMs).coerceAtLeast(System.currentTimeMillis())
        delayUntil(fadeStart)
        // 线性渐隐 fadeStart → noiseEndAt（按真实时钟推进，避免累计漂移）。
        val rampStart = System.currentTimeMillis()
        val rampMs = (noiseEndAt - rampStart).coerceAtLeast(1L)
        while (true) {
            val t = ((System.currentTimeMillis() - rampStart).toFloat() / rampMs).coerceIn(0f, 1f)
            NoiseSynth.setLayerVolume(scene, 1f - t)
            if (t >= 1f) break
            delay(1000)
        }
        // 渐隐完成 → 立即自停：onDestroy 释放 AudioTrack 并撤下前台通知，
        // 剩余时间进入纯静默，只保留 NapScheduler 的一次性唤醒闹钟。
        stopSelf()
    }

    /** 等到指定时刻；每轮按真实时钟重算剩余，避免长时间等待的累计漂移。 */
    private suspend fun delayUntil(target: Long) {
        while (true) {
            val wait = target - System.currentTimeMillis()
            if (wait <= 0L) return
            delay(min(wait, 1000L))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { NoiseSynth.stopAll() }
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
