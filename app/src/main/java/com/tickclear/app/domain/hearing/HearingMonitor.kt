package com.tickclear.app.domain.hearing

import com.tickclear.app.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.scheduler.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 听力保护监测（V2.9++）：通过动态注册广播监听耳机插拔与媒体音量变化，
 * 当「音量超过安全阈值」或「连续佩戴超过建议时长」时弹通知提醒降噪/休息。
 *
 * 说明：Android 8+ 禁止 VOLUME_CHANGED_ACTION 的静态清单接收器，故在 Application 动态注册，
 * 仅在进程存活期间生效（App 被系统杀死后监测暂停，属 v1 已知限制）。
 */
object HearingMonitor {

    private const val TAG = "HearingMonitor"
    /** VOLUME_CHANGED_ACTION 为隐藏常量，用字面量避免依赖私有 API。 */
    private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    /** 同一条超标原因的最小提醒间隔，避免频繁轰炸。 */
    private const val COOLDOWN_MS = 600_000L
    /** 佩戴计时器轮询间隔。 */
    private const val WEAR_TICK_MS = 60_000L

    private val enabled = AtomicBoolean(false)
    private val volumeThreshold = AtomicInteger(80)
    private val maxWearMin = AtomicInteger(60)
    private val connected = AtomicBoolean(false)
    private val connectAt = AtomicLong(0L)
    private val lastNotifyAt = AtomicLong(0L)

    @Volatile private var registered = false
    private var wearJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HearingEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private fun ep(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, HearingEntryPoint::class.java)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (ctx == null || intent == null) return
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG -> handlePlug(ctx, intent)
                ACTION_VOLUME_CHANGED -> evaluate(ctx)
            }
        }
    }

    /** 在 Application.onCreate 调用一次：拉取设置 + 注册动态广播。 */
    fun register(context: Context) {
        if (registered) return
        registered = true
        val settings = runCatching { ep(context).settingsRepository() }.getOrNull()
        if (settings != null) {
            // 设置流缓存在原子字段，onReceive（非挂起）直接读取，避免阻塞广播。
            scope.launch {
                launch { settings.hearingEnabled.collect { enabled.set(it) } }
                launch { settings.hearingVolumeThreshold.collect { volumeThreshold.set(it) } }
                launch { settings.hearingMaxWearMin.collect { maxWearMin.set(it) } }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(ACTION_VOLUME_CHANGED)
        }
        runCatching { context.applicationContext.registerReceiver(receiver, filter) }
            .onFailure { AppLogger.e(TAG, "register 失败：${it.message}") }
    }

    private fun handlePlug(ctx: Context, intent: Intent) {
        // ACTION_HEADSET_PLUG 的 state extra 是契约字面量 "state"（0=未连接, 1=带麦耳机, 2=无麦耳机）。
        // 不依赖 AudioManager.EXTRA_HEADSET_STATE —— 该常量在部分 compileSdk 上不可见，直接用字面量最稳。
        val state = intent.getIntExtra("state", 0)
        val isConnected = state == 1 || state == 2
        if (isConnected) {
            if (!connected.getAndSet(true)) {
                connectAt.set(System.currentTimeMillis())
                startWearTicker(ctx)
            }
        } else {
            if (connected.getAndSet(false)) {
                stopWearTicker()
            }
        }
    }

    /** 评估当前音量占比与佩戴时长，超标则弹通知（带冷却）。 */
    private fun evaluate(ctx: Context) {
        if (!enabled.get() || !connected.get()) return
        val am = runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }.getOrNull() ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (cur * 100 / max) else 0
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt.get() < COOLDOWN_MS) return
        val wearMin = ((now - connectAt.get()) / 60000L).toInt()
        val overVolume = pct >= volumeThreshold.get()
        val overWear = wearMin >= maxWearMin.get()
        if (overVolume || overWear) {
            val reason = if (overVolume) {
                ctx.getString(R.string.hearing_notify_volume, pct)
            } else {
                ctx.getString(R.string.hearing_notify_wear, wearMin)
            }
            NotificationHelper.showHearingNotification(ctx, reason)
            lastNotifyAt.set(now)
        }
    }

    private fun startWearTicker(ctx: Context) {
        stopWearTicker()
        wearJob = scope.launch {
            while (isActive) {
                delay(WEAR_TICK_MS)
                evaluate(ctx)
            }
        }
    }

    private fun stopWearTicker() {
        wearJob?.cancel()
        wearJob = null
    }
}
