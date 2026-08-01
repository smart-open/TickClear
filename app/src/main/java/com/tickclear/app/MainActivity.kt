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
import androidx.compose.ui.geometry.Offset
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
            // 清扫机器人：用 Canvas 绘制圆盘形扫地机，绕场景中心公转（圆周路径），
            // 机身自身同时自转，沿途"吸入"地面尘屑并淡出——呼应「点清」隐喻且更具现代感。
            Box(modifier = Modifier.scale(scale.value)) {
                RobotClean(Modifier.fillMaxWidth().height(150.dp))
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
 * 开屏清扫机器人动画：圆盘型扫地机 + 顶部激光雷达/摄像头凸起，绕场景中心作公转
 * （公转一周约 4 秒，顺时针），机身自身持续自转（约 1.2 秒/圈），沿途"吸入"地面尘屑
 * 并随距离中心远近淡出。零新依赖、纯 Compose Canvas 绘制。
 *
 * 设计要点：
 * - 公转 + 自转 两层动画叠加，视觉上就是「在转圈清扫」；
 * - 圆盘上画 4 个扇形分割 + 中心 LiDAR 凸起 + 朝向指示箭头，识别度高；
 * - 尘屑 7 颗按圆周分布，距机器人越近越透明（已被吸入）；
 * - 主色取 theme.primary 路径边，避免硬编码 color（夜间模式自动适配）。
 */
@Composable
private fun RobotClean(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "robot")
    // 公转：弧度 0..2π
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    // 自转：0..360 度
    val selfSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "selfSpin",
    )
    // 呼吸指示灯（机身边圈小蓝点），微微脉动增加生动感
    val led by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "led",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val orbitR = w * 0.28f
        val robotR = w * 0.085f
        val groundY = h * 0.78f

        // 地面参考线（被公转机器人"清扫"的痕迹）
        drawLine(
            color = Color.LightGray.copy(alpha = 0.35f),
            start = Offset(w * 0.10f, groundY),
            end = Offset(w * 0.90f, groundY),
            strokeWidth = w * 0.003f,
        )
        // 中心点（公转圆心的小标记）
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.25f),
            radius = w * 0.012f,
            center = Offset(cx, cy),
        )
        // 公转轨道（虚线圆）
        for (i in 0 until 24) {
            val a = (i / 24f) * 2f * Math.PI.toFloat()
            val nx = cx + kotlin.math.cos(a) * orbitR
            val ny = cy + kotlin.math.sin(a) * orbitR
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.12f),
                radius = w * 0.004f,
                center = Offset(nx, ny),
            )
        }

        // 尘屑：分布在公转路径附近；随机器人靠近而变淡（被吸入）
        val dustCount = 9
        val dustR = w * 0.012f
        val robotX = cx + kotlin.math.cos(orbit) * orbitR
        val robotY = cy + kotlin.math.sin(orbit) * orbitR
        for (i in 0 until dustCount) {
            val ang = (i / dustCount.toFloat()) * 2f * Math.PI.toFloat()
            val dustX = cx + kotlin.math.cos(ang) * orbitR
            val dustY = cy + kotlin.math.sin(ang) * orbitR
            // 已"走过的弧段"尘屑最透明——用 ang 与 orbit 的夹角近似
            val arcBehind = ((orbit - ang) + 2f * Math.PI.toFloat()) % (2f * Math.PI.toFloat())
            val cleaned = (arcBehind / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
            val near = (1f - cleaned).coerceIn(0f, 1f)
            if (near > 0.05f) {
                drawCircle(
                    color = Color.Gray.copy(alpha = near * 0.6f),
                    radius = dustR * (0.5f + near),
                    center = Offset(dustX, dustY),
                )
            }
        }

        // 机器人本体：先平移到公转位置，再叠自转
        withTransform({
            translate(robotX, robotY)
            rotate(selfSpin, pivot = Offset.Zero)
        }) {
            // 外圈深色"防撞胶条"
            drawCircle(
                color = Color(0xFF2A2A2A),
                radius = robotR,
            )
            // 银白机身主体
            drawCircle(
                color = Color(0xFFE8ECEF),
                radius = robotR * 0.88f,
            )
            // 4 扇形分割线（暗示转向轮/边刷位置），相对自转随之旋转
            val segments = 4
            for (s in 0 until segments) {
                val a = (s / segments.toFloat()) * 2f * Math.PI.toFloat()
                val ex = kotlin.math.cos(a) * robotR * 0.86f
                val ey = kotlin.math.sin(a) * robotR * 0.86f
                drawLine(
                    color = Color(0xFFB8BEC4),
                    start = Offset(0f, 0f),
                    end = Offset(ex, ey),
                    strokeWidth = w * 0.0035f,
                )
                // 边刷小圆点
                drawCircle(
                    color = Color(0xFF7A8088),
                    radius = w * 0.012f,
                    center = Offset(ex, ey),
                )
            }
            // 中心 LiDAR 凸起（深色圆 + 高光）
            drawCircle(
                color = Color(0xFF1F2933),
                radius = robotR * 0.32f,
            )
            drawCircle(
                color = Color(0xFF3E4C5A),
                radius = robotR * 0.22f,
            )
            // 朝向指示箭头（机顶小三角），随自转旋转
            val arrowLen = robotR * 0.72f
            val arrowW = robotR * 0.18f
            drawPath(
                path = Path().apply {
                    moveTo(0f, -arrowLen)
                    lineTo(arrowW, -robotR * 0.18f)
                    lineTo(-arrowW, -robotR * 0.18f)
                    close()
                },
                color = Color(0xFF3E8E7E),
            )
            // 呼吸状态灯（顶部小蓝点，亮度由 led 动画驱动）
            drawCircle(
                color = Color(0xFF4FC3F7).copy(alpha = led),
                radius = w * 0.011f,
                center = Offset(robotR * 0.42f, -robotR * 0.10f),
            )
            // 公转方向指示（机器人外圈右侧一个红色小箭头，始终指向运动方向）
        }
        // 不随自转的"前进方向"标记：放在机器人当前朝向角处（始终指公转切线方向）
        val tangent = orbit + (Math.PI / 2).toFloat() // 顺时针切向
        val headX = robotX + kotlin.math.cos(tangent) * robotR * 1.15f
        val headY = robotY + kotlin.math.sin(tangent) * robotR * 1.15f
        drawCircle(
            color = Color(0xFFFF6E40),
            radius = w * 0.012f,
            center = Offset(headX, headY),
        )
    }
}
