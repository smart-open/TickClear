package com.tickclear.app.ui.tools

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.tickclear.app.R
import com.tickclear.app.domain.scheduler.NotificationHelper
import kotlinx.coroutines.delay
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
        val composeView = ComposeView(this@ClockOverlayService).apply {
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
        overlayView = composeView
        runCatching { windowManager.addView(composeView, params) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
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

        /** 当前时分秒字符串（HH:mm:ss）。 */
        fun currentTime(): String =
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}

@Composable
private fun ClockOverlayContent(onClose: () -> Unit, onDrag: (Float, Float) -> Unit) {
    var timeText by remember { mutableStateOf(ClockOverlayService.currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeText = ClockOverlayService.currentTime()
        }
    }
    Box(
        modifier = Modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = " ✕",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
    }
}
