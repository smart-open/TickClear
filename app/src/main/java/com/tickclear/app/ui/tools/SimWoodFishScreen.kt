package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.runtime.withFrameMillis

/**
 * 拟物敲木鱼（V2.9++ 模拟解压）。
 * 点一下敲一下、计数 + 木鱼“笃”声 + 触觉反馈，敲出的涟漪圈提供即时视觉回报。
 * 纯 Canvas + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimWoodFishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableStateOf(0) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var punch by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
            if (punch > 0.001f) punch *= 0.85f else punch = 0f
        }
    }

    fun knock(nx: Float, ny: Float) {
        count++
        punch = 1f
        particles = particles + SimParticle(
            x = nx, y = ny, vx = 0f, vy = 0f,
            life = 0.6f, maxLife = 0.6f, hue = 30f, radius = 8f, ring = true,
        )
        FoleySynth.play("wood")
        Haptic.vibrate(context, 40)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_wood_title)) },
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
            SimHintCard(stringResource(R.string.tools_sim_wood_hint))
            Spacer(Modifier.height(Spacing.sm))
            SimStatCard(
                value = count.toString(),
                label = stringResource(R.string.tools_unit_times),
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(320.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val nx = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                            val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                            knock(nx, ny)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val s = 1f - punch * 0.05f
                    val bodyW = w * 0.56f * s
                    val bodyH = h * 0.34f * s

                    drawOval(
                        color = Color(0xFFB5835A),
                        topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                        size = Size(bodyW, bodyH),
                    )
                    drawOval(
                        color = Color(0xFFA9744C),
                        topLeft = Offset(cx - bodyW / 2f - bodyW * 0.12f, cy - bodyH * 0.32f),
                        size = Size(bodyW * 0.34f, bodyH * 0.64f),
                    )
                    drawOval(
                        color = Color(0xFFD8B48C),
                        topLeft = Offset(cx - bodyW * 0.18f, cy - bodyH * 0.30f),
                        size = Size(bodyW * 0.36f, bodyH * 0.18f),
                    )
                    drawLine(
                        color = Color(0xFF5A3A22),
                        start = Offset(cx - bodyW * 0.30f, cy + bodyH * 0.10f),
                        end = Offset(cx + bodyW * 0.10f, cy + bodyH * 0.14f),
                        strokeWidth = 4f,
                    )
                    drawOval(
                        color = Color(0xFF3A2414),
                        topLeft = Offset(cx - bodyW * 0.26f, cy - bodyH * 0.18f),
                        size = Size(bodyH * 0.10f, bodyH * 0.10f),
                    )

                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        drawOval(
                            color = simColor(pt.hue, a * 0.8f),
                            topLeft = Offset(pt.x * w - pt.radius, pt.y * h - pt.radius),
                            size = Size(pt.radius * 2, pt.radius * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * a + 1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
