package com.tickclear.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tickclear.app.ui.navigation.ShortcutHelper
import com.tickclear.app.ui.navigation.TickClearApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 开机动画每个进程只播一次（旋转重建 Activity 不重播，且不引入 runtime-saveable 新依赖）。 */
private var splashShownThisProcess = false

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // V2.9：动态快捷方式携带的启动动作，冷启动经 onCreate、热启动经 onNewIntent 更新。
    private var shortcutAction by mutableStateOf<String?>(null)

    // V2.8X++：POST_NOTIFICATIONS 运行时授权（Android 13+ 必需）。本 App 以定时提醒为核心，
    // 未授予该权限时任务/习惯/测试通知在前台/后台/锁屏三态下全部被系统静默拦截，
    // 是「通知不触发」最常见的根因。结果无需额外处理——授予则通知生效，拒绝后调试页仍提供跳系统设置的恢复入口。
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutHelper.register(this)
        ensureNotificationPermission()
        shortcutAction = intent?.getStringExtra(ShortcutHelper.EXTRA_SHORTCUT_ACTION)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppRoot(
                    startAction = shortcutAction,
                    onStartActionConsumed = { shortcutAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutAction = intent.getStringExtra(ShortcutHelper.EXTRA_SHORTCUT_ACTION)
    }

    /**
     * Android 13+（TIRAMISU）未授予 POST_NOTIFICATIONS 时发起一次运行时申请。
     * 系统在用户多次拒绝后会自动不再弹窗，故此处每次冷启动仅在「尚未授予」时尝试，行为温和；
     * 12 及以下无此运行时权限，通知随渠道即可展示，无需处理。
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * 应用根容器：主内容 [TickClearApp] 在底层正常组合并加载数据，
 * 冷启动期间在上层叠加 [LaunchSplash] 开机动画（3 秒，点击可跳过）。
 * 进程级标记防止配置变更（旋转）重播动画，且不引入 runtime-saveable 新依赖。
 */
@Composable
private fun AppRoot(
    startAction: String?,
    onStartActionConsumed: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TickClearApp(
            startAction = startAction,
            onStartActionConsumed = onStartActionConsumed,
        )
        var showSplash by remember { mutableStateOf(!splashShownThisProcess) }
        if (showSplash) {
            LaunchSplash(onDismiss = { showSplash = false; splashShownThisProcess = true })
        }
    }
}

/**
 * 开机启动动画：图标缩放淡入 + 应用名 + 标语 + 轮播的 APP 推荐文案（任务清理/习惯养成/智能助手）。
 * 3 秒后整层淡出并揭开底层已加载好的首页；点击任意位置可立即跳过。
 * 关键：整层带**不透明底色**，遮住底层首页，避免"开机动画与首页同时出现"。
 */
@Composable
private fun LaunchSplash(onDismiss: () -> Unit) {
    // 入场：内容层淡入 + 图标缩放。
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.82f) }
    // 整层透明度：退场时淡出，实现"动画结束才揭开首页"的平滑过渡。
    val splashAlpha = remember { Animatable(1f) }
    var dismissing by remember { mutableStateOf(false) }

    // 入场动画（淡入 + 图标缩放并行）。
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(600)) }
        launch { scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    }
    // 3 秒后自动进入退场。
    LaunchedEffect(Unit) {
        delay(3000)
        dismissing = true
    }
    // 退场：整层淡出后真正移除（揭开首页）。
    LaunchedEffect(dismissing) {
        if (dismissing) {
            splashAlpha.animateTo(0f, tween(300))
            onDismiss()
        }
    }

    // 推荐文案轮播（每秒切换，带淡入）。
    val features = listOf(
        stringResource(R.string.splash_feature_tasks),
        stringResource(R.string.splash_feature_habits),
        stringResource(R.string.splash_feature_assistant),
    )
    var featureIdx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            featureIdx = (featureIdx + 1) % features.size
        }
    }
    val featureAlpha = remember { Animatable(0f) }
    LaunchedEffect(featureIdx) {
        featureAlpha.snapTo(0f)
        featureAlpha.animateTo(1f, tween(400))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 不透明底色：遮住底层首页，避免"开机动画与首页同时出现"。
            .background(MaterialTheme.colorScheme.background)
            .alpha(splashAlpha.value)
            .clickable { dismissing = true },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.alpha(alpha.value),
        ) {
            // 扫帚：用 Canvas 绘制并做左右清扫（尘屑被扫起淡出），替代原静态 emoji，直观呼应「点清」隐喻。
            Box(modifier = Modifier.scale(scale.value)) {
                BroomSweep(Modifier.fillMaxWidth().height(150.dp))
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                features[featureIdx],
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(featureAlpha.value),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.splash_skip_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 开屏扫帚清扫动画：Canvas 绘制扫帚，沿地面左右往复清扫（带轻微摆动），
 * 扫过的地面尘屑被"扫起"并淡出，直观呼应「点清 / 清理」的产品隐喻。
 * 由 [LaunchSplash] 在 3 秒开机层内持续播放；零新依赖。
 */
@Composable
private fun BroomSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "broom")
    // 左右往复：归一化 -1..1
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    // 刷毛轻微抖动，增强"正在清扫"的生动感
    val wiggle by transition.animateFloat(
        initialValue = -0.14f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wiggle",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val groundY = h * 0.72f
        val amp = w * 0.30f
        val tipX = w / 2f + sweep * amp
        val tiltDeg = sweep * 15f + wiggle * 9f

        // 地面参考线
        drawLine(
            color = Color.LightGray.copy(alpha = 0.5f),
            start = Offset(w * 0.12f, groundY),
            end = Offset(w * 0.88f, groundY),
            strokeWidth = w * 0.003f,
        )

        // 尘屑：被扫帚经过时点亮并淡出
        val dustCount = 7
        val baseR = w * 0.012f
        for (i in 0 until dustCount) {
            val dx = w * (0.16f + 0.68f * i / (dustCount - 1))
            val dist = kotlin.math.abs(dx - tipX)
            val near = (1f - dist / (amp * 0.95f)).coerceIn(0f, 1f)
            if (near > 0.02f) {
                drawCircle(
                    color = Color.Gray.copy(alpha = near * 0.55f),
                    radius = baseR * (0.55f + near),
                    center = Offset(dx, groundY + h * 0.015f),
                )
            }
        }

        // 扫帚本体（刷毛在地面、手柄朝上，整体随清扫倾斜）
        withTransform({
            translate(tipX, groundY)
            rotate(tiltDeg)
        }) {
            val bristleLen = h * 0.30f
            val baseHalf = w * 0.05f
            val topHalf = w * 0.032f
            // 刷毛主体
            drawPath(
                path = Path().apply {
                    moveTo(-baseHalf, 0f)
                    lineTo(baseHalf, 0f)
                    lineTo(topHalf, -bristleLen)
                    lineTo(-topHalf, -bristleLen)
                    close()
                },
                color = Color(0xFFC89B6A),
            )
            // 刷毛丝
            val strands = 7
            for (s in 0..strands) {
                val fx = -baseHalf + (2f * baseHalf) * s / strands
                drawLine(
                    color = Color(0xFF9C6B3F),
                    start = Offset(fx, 0f),
                    end = Offset(fx * 0.5f, -bristleLen),
                    strokeWidth = w * 0.004f,
                )
            }
            // 捆扎环
            drawRect(
                color = Color(0xFF7A5230),
                topLeft = Offset(-topHalf * 1.5f, -bristleLen - h * 0.012f),
                size = Size(topHalf * 3f, h * 0.026f),
            )
            // 手柄
            val handleLen = h * 0.44f
            val handleW = w * 0.024f
            drawRoundRect(
                color = Color(0xFFB07D4F),
                topLeft = Offset(-handleW / 2f, -bristleLen - h * 0.02f - handleLen),
                size = Size(handleW, handleLen),
                cornerRadius = CornerRadius(handleW / 2f, handleW / 2f),
            )
        }
    }
}
