package com.tickclear.app.ui.tools

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tickclear.app.R
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing

/**
 * 屏幕坏点/亮点检测（V2.9++ 实用工具）。
 * 全屏铺纯色，手动凑近查看有无不亮（坏点）或常亮（亮点）。
 * 红/绿/蓝/白/黑均为纯基色，方便在对应通道上暴露缺陷。
 * 「全屏沉浸」隐藏系统栏与工具条，尽量铺满整屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadPixelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    val palette = listOf(
        stringResource(R.string.deadpixel_red) to Color(0xFFFF0000),
        stringResource(R.string.deadpixel_green) to Color(0xFF00FF00),
        stringResource(R.string.deadpixel_blue) to Color(0xFF0000FF),
        stringResource(R.string.deadpixel_white) to Color(0xFFFFFFFF),
        stringResource(R.string.deadpixel_black) to Color(0xFF000000),
    )
    var selected by remember { mutableStateOf(0) }
    val currentColor = palette[selected].second

    // 全屏时优先用返回键退出全屏，避免误退出整个页面
    BackHandler(enabled = fullscreen) { fullscreen = false }

    // 沉浸式：进入全屏时隐藏状态栏/导航栏，退出恢复（runCatching 兜底非 Activity 场景）
    DisposableEffect(fullscreen) {
        val activity = context as? Activity
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            runCatching {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        onDispose { runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) } }
    }

    Scaffold(
        topBar = if (fullscreen) {
            { }
        } else {
            {
                TopAppBar(
                    title = { Text(stringResource(R.string.deadpixel_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.deadpixel_fullscreen))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (fullscreen) {
            // 按当前底色亮度取反色，保证退出按钮与色块描边在任意纯色上都可见
            val onColor = if (currentColor.luminance() > 0.5f) Color.Black else Color.White
            Box(modifier = Modifier.fillMaxSize().background(currentColor)) {
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.FullscreenExit,
                        contentDescription = stringResource(R.string.deadpixel_exit_fullscreen),
                        tint = onColor,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    palette.forEachIndexed { idx, (label, color) ->
                        Box(
                            modifier = Modifier.size(28.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selected == idx) 3.dp else 1.dp,
                                    color = onColor,
                                    shape = CircleShape,
                                )
                                .clickable { selected = idx }
                                .semantics { contentDescription = label },
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.deadpixel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    palette.forEachIndexed { idx, (label, _) ->
                        FilterChip(
                            selected = selected == idx,
                            onClick = {
                                selected = idx
                                Haptic.vibrate(context, 12)
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.deadpixel_current, palette[selected].first),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(currentColor),
                )
            }
        }
    }
}
