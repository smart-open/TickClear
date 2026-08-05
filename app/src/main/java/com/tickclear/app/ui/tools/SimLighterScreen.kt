package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.runtime.withFrameMillis

/**
 * 虚拟打火机（V2.9++ 模拟解压）。
 * 向上滑动开盖（火石轮打火）→ 火焰动画 + “咔哒”音效；可再次滑动或点“收起”熄灭火焰。
 * 纯 Canvas + 拖拽手势 + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimLighterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lidProgress by remember { mutableStateOf(0f) } // 0 关盖 / 1 开盖
    var lit by remember { mutableStateOf(false) }
    var flicker by remember { mutableStateOf(1f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun ignite() {
        lidProgress = 1f
        lit = true
        FoleySynth.play("lighter")
        Haptic.vibrate(context, 50)
    }

    fun closeLid() {
        lidProgress = 0f
        lit = false
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            last = now
            if (lit) flicker = 0.82f + kotlin.random.Random.nextFloat() * 0.3f
            kotlinx.coroutines.delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_lighter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (lit) stringResource(R.string.tools_sim_lighter_lit) else stringResource(R.string.tools_sim_lighter_open_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(340.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            // 向上拖（delta<0）开盖，向下拖收盖
                            lidProgress = (lidProgress - delta / 240f).coerceIn(0f, 1f)
                        },
                        onDragStopped = {
                            if (lidProgress > 0.55f) {
                                lidProgress = 1f
                                if (!lit) ignite()
                            } else {
                                closeLid()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val bodyW = w * 0.30f
                    val bodyH = h * 0.5f
                    val cx = w / 2f
                    val bodyBottom = h * 0.86f
                    val bodyTop = bodyBottom - bodyH
                    val bodyX = cx - bodyW / 2f

                    drawRoundRect(
                        color = Color(0xFFC9CDD2),
                        topLeft = Offset(bodyX, bodyTop),
                        size = Size(bodyW, bodyH),
                    )
                    drawRoundRect(
                        color = Color(0xFF9AA1A9),
                        topLeft = Offset(bodyX, bodyBottom - bodyH * 0.12f),
                        size = Size(bodyW, bodyH * 0.12f),
                    )
                    drawOval(
                        color = Color(0xFF6E747B),
                        topLeft = Offset(cx - bodyW * 0.12f, bodyTop - bodyH * 0.02f),
                        size = Size(bodyW * 0.24f, bodyH * 0.05f),
                    )

                    val lift = lidProgress * bodyH * 0.42f
                    val angle = -lidProgress * 38f
                    rotate(angle, pivot = Offset(bodyX, bodyTop)) {
                        drawRoundRect(
                            color = Color(0xFFB0B6BD),
                            topLeft = Offset(bodyX, bodyTop - bodyH * 0.16f - lift),
                            size = Size(bodyW, bodyH * 0.16f),
                        )
                    }

                    if (lit) {
                        val flameH = bodyH * 0.30f * flicker
                        val flameW = bodyW * 0.22f * flicker
                        val fy = bodyTop - bodyH * 0.04f - flameH
                        drawOval(
                            color = Color(0xFFFF6A00),
                            topLeft = Offset(cx - flameW, fy),
                            size = Size(flameW * 2, flameH),
                        )
                        drawOval(
                            color = Color(0xFFFFC107),
                            topLeft = Offset(cx - flameW * 0.6f, fy + flameH * 0.2f),
                            size = Size(flameW * 1.2f, flameH * 0.6f),
                        )
                        drawOval(
                            color = Color(0xFFFFFFFF),
                            topLeft = Offset(cx - flameW * 0.3f, fy + flameH * 0.45f),
                            size = Size(flameW * 0.6f, flameH * 0.35f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { if (lit) closeLid() else ignite() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (lit) stringResource(R.string.tools_sim_lighter_close) else stringResource(R.string.tools_sim_lighter_ignite)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
