package com.tickclear.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.domain.repository.PermissionIntroRepository
import com.tickclear.app.ui.intro.PermissionsIntroScreen
import com.tickclear.app.ui.navigation.ShortcutHelper
import com.tickclear.app.ui.navigation.TickClearApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 开机动画每个进程只播一次（旋转重建 Activity 不重播，且不引入 runtime-saveable 新依赖）。 */
private var splashShownThisProcess = false

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // V2.9：动态快捷方式携带的启动动作，冷启动经 onCreate、热启动经 onNewIntent 更新。
    private var shortcutAction by mutableStateOf<String?>(null)

    /**
     * V2.13.2 首次启动权限引导状态。Hilt field-injected，由 AppRoot 订阅；
     * `false` 时在启动动画结束之后叠加引导页遮罩。
     */
    @Inject lateinit var introRepository: PermissionIntroRepository

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
                    introRepository = introRepository,
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
    introRepository: PermissionIntroRepository,
    startAction: String?,
    onStartActionConsumed: () -> Unit,
) {
    val introDone by introRepository.introDone.collectAsStateWithLifecycle(initialValue = true)
    Box(Modifier.fillMaxSize()) {
        TickClearApp(
            startAction = startAction,
            onStartActionConsumed = onStartActionConsumed,
        )
        var showSplash by remember { mutableStateOf(!splashShownThisProcess) }
        // V2.13.2 修复：引导页必须在 splash 退出**之后**才显示，否则 splash 被引导页遮住
        // 用户看不到开机动画。splashCompleted 用 remember 跟随 LaunchSplash onDismiss 置 true。
        var splashCompleted by remember { mutableStateOf(splashShownThisProcess) }
        if (showSplash) {
            LaunchSplash(onDismiss = {
                showSplash = false
                splashShownThisProcess = true
                splashCompleted = true
            })
        }
        if (splashCompleted && !introDone) {
            PermissionsIntroScreen(onClose = { /* markDone 已让 introDone=true 自动消失 */ })
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
    var featureIdx by remember { mutableIntStateOf(0) }
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
            // 清扫机器人：Canvas 绘制的圆盘扫地机，沿圆形轨道慢速转圈清扫，
            // 走过之处尘屑淡出——呼应「点清」隐喻，克制不喧宾夺主。
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
 * 开屏清扫机器人动画（极简版）：一台圆盘扫地机沿圆形轨道**慢速**转圈清扫，走过之处尘屑被吸走。
 *
 * 设计取舍（上一版过于花哨且转速过快，此处刻意做减法）：
 * - 元素只留 4 类：淡色轨道圆环、6 颗尘屑、机器人本体、机顶朝向三角；
 *   删掉了地面线、24 点轨道点阵、4 条边刷分割线、呼吸灯、橙色前进标记；
 * - 速度大幅放慢：公转 9 秒/圈（原 4 秒）、自转 5 秒/圈（原 1.2 秒），
 *   开屏总时长仅 3 秒，慢速下只走约 1/3 圈，观感从容不"抽搐"；
 * - 自转比公转慢，机身像在稳稳巡航，而不是原地打转。
 * 零新依赖，纯 Compose Canvas 绘制。
 */
@Composable
private fun RobotClean(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "robot")
    // 公转：弧度 0..2π，9 秒一圈（慢速巡航）
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    // 自转：0..360 度，5 秒一圈（比公转慢，避免视觉忙乱）
    val selfSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "selfSpin",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        // 轨道尺寸必须按**短边**算：画布是 fillMaxWidth × 150dp 的扁矩形，
        // 若按宽度取半径（如 0.26f * w），纵向会直接溢出画布把机器人截掉。
        val base = kotlin.math.min(w, h)
        val orbitR = base * 0.30f
        val robotR = base * 0.11f
        val twoPi = 2f * Math.PI.toFloat()

        // 清扫轨道：一圈极淡的实线圆环（替代原 24 点点阵，更干净）
        drawCircle(
            color = Color.Gray.copy(alpha = 0.16f),
            radius = orbitR,
            center = Offset(cx, cy),
            style = Stroke(width = base * 0.008f),
        )

        val robotX = cx + kotlin.math.cos(orbit) * orbitR
        val robotY = cy + kotlin.math.sin(orbit) * orbitR

        // 尘屑：均匀分布在轨道上，机器人刚走过的一段淡出（表示已被吸走）
        val dustCount = 6
        for (i in 0 until dustCount) {
            val ang = (i / dustCount.toFloat()) * twoPi
            val cleaned = (((orbit - ang) + twoPi) % twoPi / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
            val remain = 1f - cleaned
            if (remain > 0.05f) {
                drawCircle(
                    color = Color.Gray.copy(alpha = remain * 0.55f),
                    radius = base * 0.022f * (0.6f + remain * 0.6f),
                    center = Offset(cx + kotlin.math.cos(ang) * orbitR, cy + kotlin.math.sin(ang) * orbitR),
                )
            }
        }

        // 机器人本体：平移到公转位置后叠加自转。
        // ⚠️ 关键：translate 后必须显式 center = Offset.Zero —— drawCircle 的 center 默认取
        // 画布中心（size.center）而非变换原点，缺省会把机身画到 (cx+robotX, cy+robotY) 直接跑出画布，
        // 屏幕上只剩一个孤零零的朝向三角（drawPath 用相对坐标，反而是对的）。
        withTransform({
            translate(robotX, robotY)
            rotate(selfSpin, pivot = Offset.Zero)
        }) {
            drawCircle(color = Color(0xFF2A2A2A), radius = robotR, center = Offset.Zero)         // 防撞胶条
            drawCircle(color = Color(0xFFE8ECEF), radius = robotR * 0.86f, center = Offset.Zero) // 银白机身
            drawCircle(color = Color(0xFF1F2933), radius = robotR * 0.30f, center = Offset.Zero) // 中心 LiDAR
            // 机顶朝向三角（唯一保留的细节，用于看清"它在转"）
            drawPath(
                path = Path().apply {
                    moveTo(0f, -robotR * 0.68f)
                    lineTo(robotR * 0.20f, -robotR * 0.36f)
                    lineTo(-robotR * 0.20f, -robotR * 0.36f)
                    close()
                },
                color = Color(0xFF3E8E7E),
            )
        }
    }
}
