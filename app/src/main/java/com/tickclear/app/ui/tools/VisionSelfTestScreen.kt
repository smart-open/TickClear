package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

/** 内部子页面枚举。 */
private enum class VisionSub { HUB, COLOR, ACUITY }

/**
 * 视力自测 V2（V2.9++ 美化）：拆分为「色盲色弱」与「近视远视」两个独立子页面，
 * 入口先选模式再进入测试，避免两类题目混杂。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionSelfTestScreen(onBack: () -> Unit) {
    var sub by remember { mutableStateOf(VisionSub.HUB) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (sub) {
                                VisionSub.HUB -> R.string.tools_vision_title
                                VisionSub.COLOR -> R.string.vision_color_card_title
                                VisionSub.ACUITY -> R.string.vision_acuity_card_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (sub == VisionSub.HUB) onBack() else sub = VisionSub.HUB
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
        ) {
            when (sub) {
                VisionSub.HUB -> VisionHub(onPick = { sub = it })
                VisionSub.COLOR -> ColorBlindnessTest()
                VisionSub.ACUITY -> AcuityTest()
            }
        }
    }
}

/** 入口页：两张大卡片让用户选测试项目。 */
@Composable
private fun VisionHub(onPick: (VisionSub) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.vision_hub_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        HubCard(
            title = stringResource(R.string.vision_color_card_title),
            desc = stringResource(R.string.vision_color_card_desc),
            icon = Icons.Filled.Contrast,
            onClick = { onPick(VisionSub.COLOR) },
        )
        HubCard(
            title = stringResource(R.string.vision_acuity_card_title),
            desc = stringResource(R.string.vision_acuity_card_desc),
            icon = Icons.Filled.Visibility,
            onClick = { onPick(VisionSub.ACUITY) },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.vision_intro),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HubCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 色盲色弱测试：8 道题，答对 ≥ 7 道视为正常。 */
@Composable
private fun ColorBlindnessTest() {
    var idx by remember { mutableIntStateOf(0) }
    var errors by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    val sameLabel = stringResource(R.string.vision_same)
    val diffLabel = stringResource(R.string.vision_diff)
    val retestLabel = stringResource(R.string.vision_retest)
    val normalText = stringResource(R.string.vision_color_normal)
    val suspectText = stringResource(R.string.vision_color_suspect)
    val resultTitle = stringResource(R.string.vision_result)

    if (finished) {
        ResultCard(
            title = resultTitle,
            passed = errors <= 2,
            passText = normalText,
            failText = suspectText,
            onRetest = {
                idx = 0
                errors = 0
                finished = false
            },
            retestLabel = retestLabel,
        )
        return
    }

    val trial = COLOR_TRIALS[idx]
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.vision_question, idx + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (idx + 1) / COLOR_TRIALS.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.vision_color_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .background(trial.left, RoundedCornerShape(16.dp)),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .background(trial.right, RoundedCornerShape(16.dp)),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Button(
                onClick = {
                    if (trial.different) errors++
                    if (idx < COLOR_TRIALS.size - 1) idx++ else finished = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text(sameLabel) }
            Button(
                onClick = {
                    if (!trial.different) errors++
                    if (idx < COLOR_TRIALS.size - 1) idx++ else finished = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text(diffLabel) }
        }
    }
}

// 已移除 ColorBox 辅助函数：调用处直接用 Box + background 组合

/** 视力（近视/远视）测试：逐级减小字号，根据能看清的最小字号判断结果。 */
@Composable
private fun AcuityTest() {
    var idx by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val clearLabel = stringResource(R.string.vision_clear)
    val blurLabel = stringResource(R.string.vision_blur)
    val retestLabel = stringResource(R.string.vision_retest)
    val normalText = stringResource(R.string.vision_acuity_normal)
    val suspectText = stringResource(R.string.vision_acuity_suspect)
    val resultTitle = stringResource(R.string.vision_result)

    if (finished) {
        val finalIdx = idx
        ResultCard(
            title = resultTitle,
            passed = finalIdx >= 4,
            passText = normalText,
            failText = suspectText,
            onRetest = { idx = 0; finished = false },
            retestLabel = retestLabel,
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.vision_question, idx + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (idx + 1) / ACUITY_SIZES.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.vision_acuity_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.vision_acuity_chars),
                    fontSize = ACUITY_SIZES[idx].sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            stringResource(R.string.vision_acuity_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Button(
                onClick = {
                    if (idx < ACUITY_SIZES.size - 1) idx++ else finished = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text(clearLabel) }
            Button(
                onClick = { finished = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text(blurLabel) }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    passed: Boolean,
    passText: String,
    failText: String,
    onRetest: () -> Unit,
    retestLabel: String,
) {
    val accent = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (passed) PassBadge() else FailBadge()
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (passed) passText else failText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onRetest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(retestLabel)
            }
        }
    }
}

/**
 * 「过关」动效（V2.9++ 二巡）：受光球+勾+8 道辐射光芒，
 * 一次性入场动画（弹缩 + 光芒淡入），纯 Canvas 零新依赖。
 */
@Composable
private fun PassBadge() {
    val scale = remember { Animatable(0.55f) }
    val shine = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
        launch { shine.animateTo(1f, tween(durationMillis = 520, delayMillis = 160)) }
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.32f * scale.value

        // 8 道辐射光芒：沿圆周向外扩散，随 shine 淡入（一次动画，零常驻帧循环）。
        val shineV = shine.value
        if (shineV > 0.01f) {
            for (i in 0 until 8) {
                val ang = i * (2.0 * PI / 8.0)
                val cosA = cos(ang).toFloat()
                val sinA = sin(ang).toFloat()
                val inner = r * 1.55f
                val outer = r * (2.05f + 0.18f * shineV)
                drawLine(
                    color = accent.copy(alpha = shineV * 0.78f),
                    start = Offset(cx + cosA * inner, cy + sinA * inner),
                    end = Offset(cx + cosA * outer, cy + sinA * outer),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // 球体：接地阴影 → 受光球 → 边缘光 → 左上光泽。
        drawSoftShadow(Offset(cx, cy + r * 1.02f), r * 0.82f, r * 0.20f, 0.22f)
        fillSphere(Offset(cx, cy), r, accent, rimLight = false)
        drawRimLight(Offset(cx, cy), r, accent.lighten(0.6f), 0.40f)
        drawGloss(Offset(cx - r * 0.34f, cy - r * 0.40f), r * 0.28f, r * 0.18f, 0.42f)

        // 白色勾：左下 → 中心折点 → 右上。
        val p1 = Offset(cx - r * 0.42f, cy + r * 0.04f)
        val p2 = Offset(cx - r * 0.08f, cy + r * 0.36f)
        val p3 = Offset(cx + r * 0.46f, cy - r * 0.30f)
        drawLine(Color.White, p1, p2, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, p2, p3, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
    }
}

/**
 * 未过关徽章：纯静态（无动效，避免在不达标时给出积极反馈）。
 */
@Composable
private fun FailBadge() {
    val accent = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.32f
        drawSoftShadow(Offset(cx, cy + r * 1.02f), r * 0.82f, r * 0.20f, 0.22f)
        fillSphere(Offset(cx, cy), r, accent, rimLight = false)
        drawRimLight(Offset(cx, cy), r, accent.lighten(0.6f), 0.40f)
        drawGloss(Offset(cx - r * 0.34f, cy - r * 0.40f), r * 0.28f, r * 0.18f, 0.42f)
        // 白色叉：左上-右下 + 右上-左下。
        drawLine(
            Color.White,
            Offset(cx - r * 0.36f, cy - r * 0.36f),
            Offset(cx + r * 0.36f, cy + r * 0.36f),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(cx + r * 0.36f, cy - r * 0.36f),
            Offset(cx - r * 0.36f, cy + r * 0.36f),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
