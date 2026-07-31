package com.tickclear.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutHelper.register(this)
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
            Text("🧹", fontSize = 64.sp, modifier = Modifier.scale(scale.value))
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
