package com.tickclear.app.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.launch

/** 单次转动/滚动总时长（毫秒）。用户要求统一为 6 秒，让结果在动画结束后才揭晓。 */
private const val SPIN_DURATION = 6000

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
    var animToken by remember { mutableIntStateOf(0) }

    // 非组合上下文（onClick）内禁止调用 stringResource，故在此预取格式串与文案。
    val pickFmt = stringResource(R.string.lottery_pick_result)
    val diceFmt = stringResource(R.string.lottery_dice_result)
    val coinFmt = stringResource(R.string.lottery_coin_result)
    val heads = stringResource(R.string.lottery_coin_heads)
    val tails = stringResource(R.string.lottery_coin_tails)
    val emptyMsg = stringResource(R.string.lottery_empty)

    fun switchMode(next: String) {
        mode = next
        result = null
        animToken = 0 // 复位令牌，避免进入新模式时旧令牌触发非点击动画
    }

    fun trigger() {
        if (mode == "pick" && options.isEmpty()) {
            result = emptyMsg
            return
        }
        result = null // 动画期间不显示结果，结束才揭晓
        animToken++
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
                    onClick = { switchMode("dice") },
                    label = { Text(stringResource(R.string.lottery_dice)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = mode == "coin",
                    onClick = { switchMode("coin") },
                    label = { Text(stringResource(R.string.lottery_coin)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = mode == "pick",
                    onClick = { switchMode("pick") },
                    label = { Text(stringResource(R.string.lottery_draw)) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 抽签模式：仅此时出现选项名单
            if (mode == "pick") {
                Text(stringResource(R.string.lottery_options_title), style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 6) input = it },
                        placeholder = { Text(stringResource(R.string.lottery_option_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .requiredHeight(38.dp),
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

                PickGrid(
                    options = options,
                    token = animToken,
                    onResult = { opt -> result = java.lang.String.format(pickFmt, opt) },
                    onDelete = { vm.removeOption(it) },
                )

                if (options.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            vm.clearOptions()
                            result = null
                            animToken = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.lottery_clear))
                    }
                }
            } else {
                // 掷骰子 / 抛硬币：动画展示区，结果仅在动画结束后由回调设置
                if (mode == "dice") {
                    DiceDisplay(token = animToken) { face ->
                        result = java.lang.String.format(diceFmt, face)
                    }
                } else {
                    CoinDisplay(token = animToken) { side ->
                        result = java.lang.String.format(coinFmt, if (side) heads else tails)
                    }
                }
            }

            Button(
                onClick = { trigger() },
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

/** 抽签选项网格：2 列紧凑卡片；点击抽签时背景色高亮在选项间滚动 6s 后落定。 */
@Composable
private fun PickGrid(
    options: List<String>,
    token: Int,
    onResult: (String) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var highlightIndex by remember { mutableIntStateOf(-1) }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        if (options.isEmpty()) {
            onResult("")
            return@LaunchedEffect
        }
        animating = true
        val finalIdx = Random.nextInt(options.size)
        var i = 0
        var elapsed = 0L
        while (elapsed < SPIN_DURATION) {
            highlightIndex = i % options.size
            i++
            delay(80)
            elapsed += 80
        }
        highlightIndex = finalIdx
        animating = false
        onResult(options[finalIdx])
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // 选项内容允许重复，故 key 必须带下标：删除中间项时纯内容 key 会撞车。
        itemsIndexed(options, key = { index, option -> "$index-$option" }) { index, option ->
            val highlighted = animating && index == highlightIndex
            Card(
                modifier = Modifier.requiredHeight(40.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highlighted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                    if (!animating) {
                        IconButton(
                            onClick = { onDelete(index) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.lottery_delete),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 掷骰子动画：3D 翻滚（绕 X/Y 双轴）+ 滚动快速换面，6s 后定格为最终点数。 */
@Composable
private fun DiceDisplay(token: Int, onResult: (Int) -> Unit) {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(1) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val finalFace = Random.nextInt(1, 7)
        val spinX = launch {
            rotX.animateTo(rotX.value + 360f * 6f, tween(SPIN_DURATION, easing = LinearEasing))
        }
        val spinY = launch {
            rotY.animateTo(rotY.value + 360f * 6f, tween(SPIN_DURATION, easing = LinearEasing))
        }
        var elapsed = 0L
        while (elapsed < SPIN_DURATION) {
            shown = Random.nextInt(1, 7)
            delay(60)
            elapsed += 60
        }
        shown = finalFace
        spinX.join()
        spinY.join()
        onResult(finalFace)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    rotationX = rotX.value
                    rotationY = rotY.value
                },
        ) {
            drawDie(shown, size)
        }
    }
}

/** 抛硬币动画：绕竖轴翻转 6s，期间正反面随翻转实时切换，结束才揭晓结果。 */
@Composable
private fun CoinDisplay(token: Int, onResult: (Boolean) -> Unit) {
    val flip = remember { Animatable(0f) }
    var restSide by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val finalSide = Random.nextBoolean()
        animating = true
        val start = flip.value
        flip.snapTo(start)
        val spin = launch {
            flip.animateTo(start + 360f * 6f, tween(SPIN_DURATION, easing = LinearEasing))
        }
        var elapsed = 0L
        while (elapsed < SPIN_DURATION) {
            delay(60)
            elapsed += 60
        }
        spin.join()
        animating = false
        restSide = finalSide
        flip.snapTo(0f)
        onResult(finalSide)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        val displaySide = if (animating) {
            val front = cos(Math.toRadians(flip.value.toDouble())) >= 0
            if (front) restSide else !restSide
        } else {
            restSide
        }
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer { rotationY = if (animating) flip.value else 0f },
        ) {
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

/** 硬币：银色金属盘（参考 2020 版 1 元硬币）；正面=国徽（五角星+环），反面=1元。 */
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
    // 银色金属渐变（左上受光）
    val silver = Brush.radialGradient(
        colors = listOf(
            Color(0xFFF4F6F8),
            Color(0xFFC3C8CE),
            Color(0xFF9BA1A9),
        ),
        center = Offset(cx - r * 0.3f, cy - r * 0.3f),
        radius = r * 1.5f,
    )
    drawCircle(brush = silver, radius = r, center = Offset(cx, cy))
    // 边缘圈
    drawCircle(
        color = Color(0xFF8A9099),
        radius = r * 0.92f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.06f),
    )

    if (side) {
        // 国徽面：内环 + 中央五角星（简化国徽意象）
        drawCircle(
            color = Color(0xFF6E747C),
            radius = r * 0.66f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.03f),
        )
        drawStar(cx, cy, r * 0.34f, Color(0xFF5A6068))
    } else {
        // 1 元面
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            setColor(android.graphics.Color.BLACK)
            textSize = r * 0.66f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText("1元", cx, cy + r * 0.24f, paint)
    }
}

/** 绘制一个以 (cx,cy) 为中心、外接半径 radius 的五角星。 */
private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = Path()
    val inner = radius * 0.4f
    for (i in 0 until 10) {
        val ang = Math.toRadians(-90.0 + i * 36.0)
        val rad = if (i % 2 == 0) radius else inner
        val x = cx + (rad * kotlin.math.cos(ang)).toFloat()
        val y = cy + (rad * kotlin.math.sin(ang)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
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
