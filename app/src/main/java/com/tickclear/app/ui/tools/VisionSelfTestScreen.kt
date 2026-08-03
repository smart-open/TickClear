package com.tickclear.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

/** 单组色觉题：左右色块 + 是否真「不同」。 */
private data class ColorTrial(val left: Color, val right: Color, val different: Boolean)

private val COLOR_TRIALS = listOf(
    ColorTrial(Color(0xFFE53935), Color(0xFFE53935), false), // 同：红/红
    ColorTrial(Color(0xFFE53935), Color(0xFF43A047), true),  // 异：红/绿（经典红绿色盲混淆）
    ColorTrial(Color(0xFF1E88E5), Color(0xFF1E88E5), false), // 同：蓝/蓝
    ColorTrial(Color(0xFF43A047), Color(0xFF8D6E63), true),  // 异：绿/棕（易混淆）
    ColorTrial(Color(0xFF1E88E5), Color(0xFF8E24AA), true),  // 异：蓝/紫（易混淆）
    ColorTrial(Color(0xFF43A047), Color(0xFF43A047), false), // 同：绿/绿
    ColorTrial(Color(0xFFE53935), Color(0xFFFB8C00), true),  // 异：红/橙（部分混淆）
    ColorTrial(Color(0xFF9E9E9E), Color(0xFF9E9E9E), false), // 同：灰/灰
)

/** 视力阶梯字号（sp），从大到小。 */
private val ACUITY_SIZES = listOf(60, 44, 34, 26, 20, 15, 12, 9)
private const val ACUITY_TEXT = "上中下大小王干"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionSelfTestScreen(onBack: () -> Unit) {
    var phase by remember { mutableStateOf("intro") } // intro | color | acuity | result
    var trialIdx by remember { mutableStateOf(0) }
    var colorErrors by remember { mutableStateOf(0) }
    var acuityIdx by remember { mutableStateOf(0) }

    // 非组合上下文（onClick）内禁止调用 stringResource，统一在此预取。
    val introText = stringResource(R.string.vision_intro)
    val startLabel = stringResource(R.string.vision_start)
    val colorTitle = stringResource(R.string.vision_color_title)
    val sameLabel = stringResource(R.string.vision_same)
    val diffLabel = stringResource(R.string.vision_diff)
    val acuityTitle = stringResource(R.string.vision_acuity_title)
    val clearLabel = stringResource(R.string.vision_clear)
    val blurLabel = stringResource(R.string.vision_blur)
    val resultTitle = stringResource(R.string.vision_result)
    val retestLabel = stringResource(R.string.vision_retest)
    val colorNormal = stringResource(R.string.vision_color_normal)
    val colorSuspect = stringResource(R.string.vision_color_suspect)
    val acuityNormal = stringResource(R.string.vision_acuity_normal)
    val acuitySuspect = stringResource(R.string.vision_acuity_suspect)

    fun reset() {
        phase = "intro"
        trialIdx = 0
        colorErrors = 0
        acuityIdx = 0
    }

    fun onColorAnswer(userSaysDifferent: Boolean) {
        val trial = COLOR_TRIALS[trialIdx]
        if (userSaysDifferent != trial.different) colorErrors++
        if (trialIdx < COLOR_TRIALS.size - 1) {
            trialIdx++
        } else {
            phase = "acuity"
            acuityIdx = 0
        }
    }

    fun onAcuityAnswer(canRead: Boolean) {
        if (canRead) {
            if (acuityIdx < ACUITY_SIZES.size - 1) {
                acuityIdx++
            } else {
                phase = "result" // 最小字号也能读 → 正常
            }
        } else {
            phase = "result" // 当前字号看不清 → 极限为 acuityIdx
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_vision_title)) },
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
            when (phase) {
                "intro" -> {
                    Text(introText, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { phase = "color" }, modifier = Modifier.fillMaxWidth()) {
                        Text(startLabel)
                    }
                }

                "color" -> {
                    val trial = COLOR_TRIALS[trialIdx]
                    Text(
                        stringResource(R.string.vision_question, trialIdx + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { (trialIdx + 1) / COLOR_TRIALS.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(colorTitle, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .background(trial.left),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .background(trial.right),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = { onColorAnswer(false) },
                            modifier = Modifier.weight(1f),
                        ) { Text(sameLabel) }
                        Button(
                            onClick = { onColorAnswer(true) },
                            modifier = Modifier.weight(1f),
                        ) { Text(diffLabel) }
                    }
                }

                "acuity" -> {
                    Text(
                        stringResource(R.string.vision_question, acuityIdx + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { (acuityIdx + 1) / ACUITY_SIZES.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(acuityTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        ACUITY_TEXT,
                        fontSize = ACUITY_SIZES[acuityIdx].sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = { onAcuityAnswer(true) },
                            modifier = Modifier.weight(1f),
                        ) { Text(clearLabel) }
                        Button(
                            onClick = { onAcuityAnswer(false) },
                            modifier = Modifier.weight(1f),
                        ) { Text(blurLabel) }
                    }
                }

                "result" -> {
                    val colorResult = if (colorErrors <= 2) colorNormal else colorSuspect
                    val acuityResult = if (acuityIdx >= 4) acuityNormal else acuitySuspect
                    Text(resultTitle, style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            colorResult,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            acuityResult,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    Button(onClick = { reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text(retestLabel)
                    }
                }
            }
        }
    }
}
