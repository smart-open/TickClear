package com.tickclear.app.ui.tools

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tickclear.app.MainActivity
import com.tickclear.app.R
import com.tickclear.app.domain.scheduler.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面悬浮时钟前台服务（V2.9++）。
 * 经 TYPE_APPLICATION_OVERLAY 在其它应用上层显示一个极简时钟，可拖拽、可关闭。
 * 需 SYSTEM_ALERT_WINDOW 权限（经系统设置页授权，非运行时权限）。
 */
class ClockOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private val params by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Service 无 Activity，ComposeView 在 onAttachedToWindow 时会同时索取 ViewTreeLifecycleOwner
        // 与 ViewTreeSavedStateRegistryOwner，缺任一个都抛 IllegalStateException。
        // 该异常发生在 addView 之后的下一帧 traversal 里，runCatching(addView) 兜不住，会直接崩主进程。
        val owner = ServiceLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner
        val composeView = ComposeView(this@ClockOverlayService).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ClockOverlayContent(
                    onClose = { stopSelf() },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        runCatching { windowManager.updateViewLayout(this@apply, params) }
                    },
                )
            }
        }
        owner.onStart()
        overlayView = composeView
        val added = runCatching { windowManager.addView(composeView, params) }.isSuccess
        if (!added) {
            // 悬浮窗权限被回收等情况：挂不上就别留一个只有通知没有时钟的僵尸服务
            overlayView = null
            stopSelf()
            return
        }
        isRunning = true
        isRunningFlow.value = true
    }

    // FOREGROUND_SERVICE_TYPE_SPECIAL_USE 是 API 34 引入的常量，但会在编译期内联为字面量 0x40000000，
    // 且 manifest 的 foregroundServiceType 是 flags 属性、由 aapt2 按 compileSdk 34 解析成同一数值写入二进制清单，
    // 因此 API 29~33 系统做「传入类型 ⊆ 清单声明类型」校验时同样通过，不会抛 IllegalArgumentException。
    // 不能标 @RequiresApi：onStartCommand 是框架回调，低版本设备上系统一样会调用它。
    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 三参 startForeground(类型) 自 API 29(Q) 才有；低于 Q 无前台服务类型概念，退回两参。
        // manifest 的 foregroundServiceType 属性在 <Q 被系统忽略，故两参路径安全且不缺权限。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isRunningFlow.value = false
        lifecycleOwner?.onStop()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun buildNotification(): Notification {
        // 显式指向 MainActivity，避免 getLaunchIntentForPackage 在部分 ROM 冻结/隐藏时返回 null
        // 导致 PendingIntent 构造抛 NPE，进而连带触发前台服务启动崩溃。
        val launch = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            NOTIF_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CLOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tools_clock_overlay_title))
            .setContentText(getString(R.string.clock_overlay_running))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIF_ID = 9401

        /**
         * 悬浮时钟是否在显示。设置页离开再回来时按此还原按钮文案，
         * 避免「界面显示未开启、实际悬浮窗还挂着」的状态错位。
         * 用 StateFlow 暴露，页面以 collectAsStateWithLifecycle 实时跟随，
         * 即使悬浮窗在前台（本页仍 RESUMED）被用户点 ✕ 关闭，按钮也能立即同步。
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 与 [isRunning] 同源的响应式流，供 Compose 页面收集。 */
        val isRunningFlow = MutableStateFlow(false)

        /** 当前时分秒字符串（HH:mm:ss）。 */
        fun currentTime(): String =
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}

@Composable
private fun ClockOverlayContent(onClose: () -> Unit, onDrag: (Float, Float) -> Unit) {
    var timeText by remember { mutableStateOf(ClockOverlayService.currentTime()) }
    // 用主线程 Handler 递归 post 来计时，独立于 Compose recomposer 生命周期：
    // 不依赖 LaunchedEffect 的协程作用域，长时间运行也不会因重组/生命周期变化而静默取消。
    // 每次对齐到下一整秒边界，消除 delay(1000) 累积漂移，与手机时间严格同步。
    val handler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        val ticker = object : Runnable {
            override fun run() {
                timeText = ClockOverlayService.currentTime()
                val delayMs = 1000L - (System.currentTimeMillis() % 1000L)
                handler.postDelayed(this, delayMs)
            }
        }
        handler.post(ticker)
        onDispose { handler.removeCallbacks(ticker) }
    }

    // 透明科技感配色：青蓝霓虹描边 + 发光数字 + 脉冲信号点
    val cyan = Color(0xFF00E5FF)
    val blue = Color(0xFF2979FF)
    val glow = Color(0x8C00E5FF)
    val pulse by rememberInfiniteTransition(label = "clockPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            // 霓虹外发光：用 shadow 模拟青色光晕（背景透明，仅边缘辉光）
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = glow,
                spotColor = glow,
                clip = false,
            )
            // 玻璃质感：极淡竖向渐变 + 透明，不遮挡内容
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x1F0A1B2B), Color(0x0D06121F)),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            // 青→蓝霓虹描边
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(cyan.copy(alpha = 0.85f), blue.copy(alpha = 0.85f)),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 脉冲信号点：科技感"运行中"指示
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = cyan.copy(alpha = pulse),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = timeText,
                color = Color(0xFFE6FBFF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(
                        color = cyan.copy(alpha = 0.9f),
                        offset = Offset(0f, 0f),
                        blurRadius = 12f,
                    ),
                ),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.clock_overlay_close),
                tint = cyan.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose),
            )
        }
    }
}

/**
 * 为悬浮窗 ComposeView 提供 Service 作用域的 LifecycleOwner + SavedStateRegistryOwner。
 * 两者缺一不可：Compose 的 AndroidComposeView.onAttachedToWindow 会分别 checkNotNull 这两个 owner。
 * performRestore 必须在 lifecycle 仍为 INITIALIZED 时调用，故顺序为「先 restore 再置 CREATED」。
 */
private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() { registry.currentState = Lifecycle.State.RESUMED }
    fun onStop() { registry.currentState = Lifecycle.State.CREATED }
    fun onDestroy() { registry.currentState = Lifecycle.State.DESTROYED }
}
