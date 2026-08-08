package com.tickclear.app.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.random.Random
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(
    vm: LotteryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val options by vm.options.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("pick") } // pick / dice / coin

    // 动画状态：骰子点数、硬币正反面、以及每次"掷/抛"递增的触发令牌
    var diceFace by remember { mutableIntStateOf(1) }
    var coinSide by remember { mutableStateOf(true) } // true=正面
    var animToken by remember { mutableIntStateOf(0) }

    // 非组合上下文（onClick）内禁止调用 stringResource，故在此预取格式串与文案。
    val pickFmt = stringResource(R.string.lottery_pick_result)
    val diceFmt = stringResource(R.string.lottery_dice_result)
    val coinFmt = stringResource(R.string.lottery_coin_result)
    val heads = stringResource(R.string.lottery_coin_heads)
    val tails = stringResource(R.string.lottery_coin_tails)
    val emptyMsg = stringResource(R.string.lottery_empty)

    fun roll() {
        result = when (mode) {
            "dice" -> {
                val f = Random.nextInt(1, 7)
                diceFace = f
                animToken++
                java.lang.String.format(diceFmt, f)
            }
            "coin" -> {
                val h = Random.nextBoolean()
                coinSide = h
                animToken++
                java.lang.String.format(coinFmt, if (h) heads else tails)
            }
            else -> if (options.isNotEmpty()) {
                java.lang.String.format(pickFmt, options[Random.nextInt(options.size)])
            } else {
                emptyMsg
            }
        }
    }

    val actionLabel = when (mode) {
        "dice" -> stringResource(R.string.lottery_action_dice)
        "coin" -> stringResource(R.string.lottery_action_coin)
        else -> stringResource(R.string.lottery_action_pick)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_lottery_title)) },
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
            // 模式切换置顶：掷骰子 → 抛硬币 → 抽签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = mode == "dice",
                    onClick = { mode = "dice" },
                    label = { Text(stringResource(R.string.lottery_dice)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = mode == "coin",
                    onClick = { mode = "coin" },
                    label = { Text(stringResource(R.string.lottery_coin)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = mode == "pick",
                    onClick = { mode = "pick" },
                    label = { Text(stringResource(R.string.lottery_draw)) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 抽签模式：仅此时出现选项名单（列表高度已压缩）
            if (mode == "pick") {
                Text(stringResource(R.string.lottery_options_title), style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.lottery_option_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            vm.addOption(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lottery_add))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // 选项内容允许重复，故 key 必须带下标：删除中间项时纯内容 key 会撞车，
                    // 纯下标（缺省行为）又会让后续项全部错位复用。
                    itemsIndexed(options, key = { index, option -> "$index-$option" }) { index, option ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(option, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { vm.removeOption(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lottery_delete))
                                }
                            }
                        }
                    }
                }

                if (options.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            vm.clearOptions()
                            result = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.lottery_clear))
                    }
                }
            } else {
                // 掷骰子 / 抛硬币：动画展示区
                if (mode == "dice") DiceDisplay(face = diceFace, token = animToken)
                else CoinDisplay(side = coinSide, token = animToken)
            }

            Button(
                onClick = { roll() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }

            if (result != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.lottery_result),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            result ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** 掷骰子动画：滚动期间快速换面，落地定格为最终点数，并旋转三圈。 */
@Composable
private fun DiceDisplay(face: Int, token: Int) {
    val rotation = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(face) }
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        repeat(8) {
            shown = Random.nextInt(1, 7)
            delay(45)
        }
        shown = face
        rotation.animateTo(
            rotation.value + 360f * 3f,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(110.dp).graphicsLayer { rotationZ = rotation.value }) {
            drawDie(shown, size)
        }
    }
}

/** 抛硬币动画：绕竖轴翻转两圈，正面/反面随翻转实时切换。 */
@Composable
private fun CoinDisplay(side: Boolean, token: Int) {
    val flip = remember { Animatable(0f) }
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val start = flip.value
        flip.snapTo(start)
        flip.animateTo(
            start + 360f * 2f,
            animationSpec = tween(620, easing = FastOutSlowInEasing),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        val front = cos(Math.toRadians(flip.value.toDouble())) >= 0
        val displaySide = if (front) side else !side
        Canvas(modifier = Modifier.size(110.dp).graphicsLayer { rotationY = flip.value }) {
            drawCoin(displaySide, size)
        }
    }
}

/** 骰子：受光立方体 + 点位。 */
private fun DrawScope.drawDie(face: Int, size: Size) {
    val r = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawSoftShadow(
        center = Offset(cx, cy + r * 0.06f),
        radiusX = r * 0.52f,
        radiusY = r * 0.5f,
        maxAlpha = 0.18f,
    )
    val pad = r * 0.06f
    fillRoundRect3D(
        topLeft = Offset(pad, pad),
        size = Size(size.width - 2 * pad, size.height - 2 * pad),
        cornerRadius = r * 0.18f,
        base = Color(0xFFF5F5F0),
    )
    val pipColor = Color(0xFF212121)
    val u = r * 0.24f
    val pr = r * 0.09f
    for (p in dicePips(face)) {
        drawCircle(pipColor, pr, Offset(cx + p.first * u, cy + p.second * u))
    }
}

/** 硬币：金色受光球 + 内圈 + 正/反字样。 */
private fun DrawScope.drawCoin(side: Boolean, size: Size) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawSoftShadow(
        center = Offset(cx, cy + r * 0.08f),
        radiusX = r * 0.62f,
        radiusY = r * 0.6f,
        maxAlpha = 0.18f,
    )
    fillSphere(center = Offset(cx, cy), radius = r * 0.92f, base = Color(0xFFD4AF37))
    drawCircle(
        color = Color.Black.copy(alpha = 0.18f),
        radius = r * 0.7f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.05f),
    )
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        setColor(android.graphics.Color.WHITE)
        textSize = r * 0.72f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        if (side) "正" else "反",
        cx,
        cy + r * 0.25f,
        paint,
    )
}

/** 标准骰子点位布局，返回相对中心的归一化坐标（单位 u）。 */
private fun dicePips(face: Int): List<Pair<Float, Float>> = when (face) {
    1 -> listOf(0f to 0f)
    2 -> listOf(-1f to -1f, 1f to 1f)
    3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
    4 -> listOf(-1f to -1f, -1f to 1f, 1f to -1f, 1f to 1f)
    5 -> listOf(-1f to -1f, -1f to 1f, 0f to 0f, 1f to -1f, 1f to 1f)
    else -> listOf(-1f to -1f, -1f to 0f, -1f to 1f, 1f to -1f, 1f to 0f, 1f to 1f)
}
