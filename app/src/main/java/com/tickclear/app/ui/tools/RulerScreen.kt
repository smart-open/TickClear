package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 每毫米对应的像素数：屏幕 XDPI（约值，仅用于简易测量）。
    val pxPerMm = context.resources.displayMetrics.xdpi / 25.4f

    var startX by remember { mutableStateOf(0f) }
    var endX by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }
    var endSet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_ruler_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.ruler_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            val lengthCm = if (initialized) abs(endX - startX) / pxPerMm / 10f else 0f
            Text(
                stringResource(R.string.ruler_length, lengthCm),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.ruler_calibrated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (!initialized) {
                                startX = offset.x
                                endX = offset.x
                                initialized = true
                                endSet = false
                            } else if (!endSet) {
                                endX = offset.x
                                endSet = true
                            } else {
                                startX = offset.x
                                endX = offset.x
                                endSet = false
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val baseY = h * 0.55f
                    val primary = Color.Gray

                    // 基线
                    drawLine(primary, Offset(0f, baseY), Offset(w, baseY), strokeWidth = 2f)

                    // 刻度：每毫米一格，每 5mm 中刻度，每 10mm（1cm）长刻度
                    val totalMm = (w / pxPerMm).toInt()
                    for (i in 0..totalMm) {
                        val x = i * pxPerMm
                        val tickH = when {
                            i % 10 == 0 -> 28f
                            i % 5 == 0 -> 16f
                            else -> 9f
                        }
                        drawLine(primary, Offset(x, baseY - tickH), Offset(x, baseY), strokeWidth = 1.5f)
                    }

                    // 测量区间高亮
                    if (initialized) {
                        val left = minOf(startX, endX).coerceIn(0f, w)
                        val right = maxOf(startX, endX).coerceIn(0f, w)
                        drawLine(
                            color = MaterialTheme.colorScheme.primary,
                            start = Offset(left, baseY - 36f),
                            end = Offset(right, baseY - 36f),
                            strokeWidth = 4f,
                        )
                        if (endSet && right > left) {
                            drawLine(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                start = Offset(left, 0f),
                                end = Offset(right, h),
                                strokeWidth = 1f,
                            )
                        }
                        // 两端把手
                        drawCircle(
                            color = MaterialTheme.colorScheme.primary,
                            radius = 8f,
                            center = Offset(left, baseY - 36f),
                        )
                        drawCircle(
                            color = MaterialTheme.colorScheme.primary,
                            radius = 8f,
                            center = Offset(right, baseY - 36f),
                        )
                    }
                }
            }

            if (initialized && !endSet) {
                Text(
                    stringResource(R.string.ruler_tap_second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
