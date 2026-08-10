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
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 单次转动/滚动总时长（毫秒）。用户要求统一为 3 秒，结果在动画结束后才揭晓。 */
private const val SPIN_DURATION = 3000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(
    vm: LotteryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val options by vm.options.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("dice") } // pick / dice / coin
    var animToken by remember { mutableIntStateOf(0) }
    var animating by remember { mutableStateOf(false) }

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
        animating = false
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
                            .requiredHeight(56.dp),
                    )
                    FilledIconButton(
                        onClick = {
                            vm.addOption(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lottery_add))
                    }
                }

                PickGrid(
                    options = options,
                    token = animToken,
                    onResult = { opt -> result = java.lang.String.format(pickFmt, opt) },
                    onAnimatingChange = { animating = it },
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
                    DiceDisplay(token = animToken, onAnimatingChange = { animating = it }) { face ->
                        result = java.lang.String.format(diceFmt, face)
                    }
                } else {
                    CoinDisplay(token = animToken, onAnimatingChange = { animating = it }) { side ->
                        result = java.lang.String.format(coinFmt, if (side) heads else tails)
                    }
                }
            }

            Button(
                onClick = { trigger() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !animating,
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
    onAnimatingChange: (Boolean) -> Unit,
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
        onAnimatingChange(true)
        try {
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
            onResult(options[finalIdx])
        } finally {
            animating = false
            onAnimatingChange(false)
        }
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

/** 掷骰子动画：2D 骰子面循环切换点数（1-6），配合轻微 3D 翻滚，3s 后定格为等概率随机点数。 */
@Composable
private fun DiceDisplay(
    token: Int,
    onAnimatingChange: (Boolean) -> Unit,
    onResult: (Int) -> Unit,
) {
    var shown by remember { mutableIntStateOf(1) }
    val tumble = remember { Animatable(0f) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        onAnimatingChange(true)
        val finalFace = Random.nextInt(1, 7)
        try {
            val spin = launch {
                tumble.animateTo(1f, tween(SPIN_DURATION, easing = LinearEasing))
            }
            var elapsed = 0L
            while (elapsed < SPIN_DURATION) {
                shown = Random.nextInt(1, 7)
                delay(80)
                elapsed += 80
            }
            shown = finalFace
            spin.join()
        } finally {
            onAnimatingChange(false)
        }
        onResult(finalFace)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    // 旋转量取 360° 的整数倍，翻滚结束后恰好回到正向，不镜像
                    rotationX = tumble.value * 720f
                    rotationY = tumble.value * 360f
                },
        ) {
            drawDieFace(shown, size)
        }
    }
}

/** 抛硬币动画：绕竖轴翻转 3s，期间正反面随翻转实时切换，结束才揭晓结果。 */
@Composable
private fun CoinDisplay(
    token: Int,
    onAnimatingChange: (Boolean) -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val flip = remember { Animatable(0f) }
    var restSide by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val finalSide = Random.nextBoolean()
        animating = true
        onAnimatingChange(true)
        try {
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
            restSide = finalSide
            flip.snapTo(0f)
        } finally {
            animating = false
            onAnimatingChange(false)
        }
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

/**
 * 2D 骰子面（立体强化版）：投影 + 左上受光渐变面 + 右下倒角暗边 + 米色描边，
 * 黑点内凹（右下缘高光）。保留标准点位坐标，绝不随旋转错位。
 */
private fun DrawScope.drawDieFace(value: Int, size: Size) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val face = size.minDimension * 0.82f
    val half = face / 2f
    val corner = CornerRadius(face * 0.20f)

    // 接地投影：让骰子"浮"在桌面上，产生立体悬浮感
    drawSoftShadow(
        center = Offset(cx, cy + half * 0.12f),
        radiusX = half * 0.96f,
        radiusY = half * 0.96f,
        maxAlpha = 0.22f,
    )

    // 面主体：左上受光的白→米白渐变，模拟圆角塑料面的体积
    val faceGrad = Brush.linearGradient(
        colors = listOf(Color(0xFFFEFEFE), Color(0xFFF6F2EA), Color(0xFFE6E0D4)),
        start = Offset(cx - half, cy - half),
        end = Offset(cx + half, cy + half),
    )
    drawRoundRect(
        brush = faceGrad,
        topLeft = Offset(cx - half, cy - half),
        size = Size(face, face),
        cornerRadius = corner,
    )

    // 右下暗角：叠加半透明黑，强化倒角厚度
    val bevel = Brush.linearGradient(
        colors = listOf(Color(0x00000000), Color(0x00000000), Color(0x26000000)),
        start = Offset(cx, cy),
        end = Offset(cx + half, cy + half),
    )
    drawRoundRect(
        brush = bevel,
        topLeft = Offset(cx - half, cy - half),
        size = Size(face, face),
        cornerRadius = corner,
    )

    // 边缘描边（米色），界定骰子轮廓
    drawRoundRect(
        color = Color(0xFFD8D2C4),
        topLeft = Offset(cx - half, cy - half),
        size = Size(face, face),
        cornerRadius = corner,
        style = Stroke(width = face * 0.045f),
    )

    // 黑点（标准骰子布局，居中对齐）+ 内凹高光（右下缘受光，呈凹陷感）
    val pipR = face * 0.085f
    val reach = half * 0.68f
    for ((px, py) in dicePips(value)) {
        val x = cx + px * reach
        val y = cy + py * reach
        drawCircle(Color(0xFFFFFFFF), pipR * 0.85f, Offset(x + pipR * 0.25f, y + pipR * 0.25f))
        drawCircle(Color(0xFF2A2A2A), pipR, Offset(x, y))
    }
}

/** 硬币主体：银色金属盘 + 边缘双圈 + 内圈。 */
private fun DrawScope.drawCoinBody(size: Size) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawSoftShadow(
        center = Offset(cx, cy + r * 0.08f),
        radiusX = r * 0.62f,
        radiusY = r * 0.6f,
        maxAlpha = 0.18f,
    )
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
    drawCircle(
        color = Color(0xFF8A9099),
        radius = r * 0.92f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.06f),
    )
    drawCircle(
        color = Color(0xFF8A9099),
        radius = r * 0.78f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.02f),
    )
}

/** 硬币：side=true 为正面（1 元牡丹花），false 为背面（国徽）。 */
private fun DrawScope.drawCoin(side: Boolean, size: Size) {
    drawCoinBody(size)
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    if (side) drawCoinObverse(cx, cy, r) else drawNationalEmblem(cx, cy, r)
}

/**
 * 硬币周边装饰：在半径 ringR 处均匀绘制 n 颗小圆点，
 * 参考 1 元牡丹花币与国徽币实物都有"周边一排小圆点装饰"（珠圈纹）。
 * 默认 n=44、ringR=0.86r、dotR=0.018r——视觉上饱满而不喧宾夺主。
 */
private fun DrawScope.drawCoinBorderDots(
    cx: Float,
    cy: Float,
    r: Float,
    color: Color,
    n: Int = 44,
    ringR: Float = 0.86f * r,
    dotR: Float = 0.018f * r,
) {
    for (i in 0 until n) {
        val ang = Math.toRadians((i * 360f / n).toDouble())
        val x = cx + (ringR * cos(ang)).toFloat()
        val y = cy + (ringR * sin(ang)).toFloat()
        drawCircle(color = color, radius = dotR, center = Offset(x, y))
    }
}

/**
 * 正面：周边装饰珠圈 + 牡丹花（左下）+ 中央"1" + 右侧"元"+ 下方"YI YUAN"拼音，
 * 参考实物 2019 版 1 元硬币（牡丹 + 元 + YI YUAN）。**简化的"双层 5+5 圆 + 中心圆"作为牡丹花纹理**——
 * 与国徽大小匹配、肉眼可识别为"花"。
 */
private fun DrawScope.drawCoinObverse(cx: Float, cy: Float, r: Float) {
    val canvas = drawContext.canvas.nativeCanvas
    val dark = 0xFF3A3D42.toInt()
    val darkColor = Color(dark)

    // 1) 周边小圆点装饰
    drawCoinBorderDots(cx, cy, r, darkColor)

    // 2) 牡丹花（"1" 的左上方）
    drawPeonyFlower(cx - r * 0.30f, cy - r * 0.14f, r * 0.20f, darkColor)

    // 3) 中央"1"（粗体超大）
    val num1Paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.62f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("1", cx + r * 0.10f, cy + r * 0.30f, num1Paint)

    // 4) 右侧"元"（小一号）
    val yuanPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.34f
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("元", cx + r * 0.34f, cy + r * 0.18f, yuanPaint)

    // 5) 下方"YI YUAN"拼音
    val pinyinPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.14f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT
    }
    canvas.drawText("YI YUAN", cx, cy + r * 0.60f, pinyinPaint)
}

/**
 * 简化牡丹花：外层 5 圆 + 内层 5 圆错位 + 中心实心圆，共 11 个圆形叠成饱满花型。
 * [cx, cy] 花心位置；[radius] 外层花瓣中心距花心的距离（决定整体大小）。
 */
private fun DrawScope.drawPeonyFlower(cx: Float, cy: Float, radius: Float, color: Color) {
    val outerR = radius * 0.46f
    val dist = radius * 0.55f
    // 外层 5 花瓣（90° 起每隔 72°）
    for (i in 0 until 5) {
        val ang = Math.toRadians((-90.0 + i * 72.0))
        val ox = (dist * cos(ang)).toFloat()
        val oy = (dist * sin(ang)).toFloat()
        drawCircle(color = color, radius = outerR, center = Offset(cx + ox, cy + oy))
    }
    // 内层 5 花瓣（与外层错位 36°），半径略小
    val innerR = outerR * 0.72f
    val innerDist = dist * 0.62f
    for (i in 0 until 5) {
        val ang = Math.toRadians((-90.0 + 36.0 + i * 72.0))
        val ox = (innerDist * cos(ang)).toFloat()
        val oy = (innerDist * sin(ang)).toFloat()
        drawCircle(color = color, radius = innerR, center = Offset(cx + ox, cy + oy))
    }
    // 中心花蕊
    drawCircle(color = color, radius = radius * 0.32f, center = Offset(cx, cy))
}

/**
 * 背面：周边装饰珠圈 + 顶部弧形"ZHONGHUA RENMIN GONGHEGUO" + 标准国徽（大星 + 四小星 + 天安门 + 麦穗齿轮）
 * + 下方"中华人民共和国"+ 年份。参照 2000 年版 1 元硬币实物图。
 */
private fun DrawScope.drawNationalEmblem(cx: Float, cy: Float, r: Float) {
    val emblem = Color(0xFF3A3D42)
    val disc = Color(0xFFC3C8CE)
    val canvas = drawContext.canvas.nativeCanvas
    val dark = 0xFF3A3D42.toInt()

    // 1) 周边小圆点装饰
    drawCoinBorderDots(cx, cy, r, emblem)

    // 2) 顶部弧形"ZHONGHUA RENMIN GONGHEGUO"（沿外圈内侧）
    val oval = android.graphics.RectF(
        cx - r * 0.78f, cy - r * 0.78f,
        cx + r * 0.78f, cy + r * 0.78f,
    )
    val arcPath = android.graphics.Path().apply { arcTo(oval, 200f, 140f) }
    val arcPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.115f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawTextOnPath("ZHONGHUA RENMIN GONGHEGUO", arcPath, 0f, -r * 0.012f, arcPaint)

    // 3) 国徽主体（大星 + 四小星）
    drawStar(cx, cy - r * 0.30f, r * 0.17f, emblem)
    val smallR = r * 0.07f
    val angles = listOf(205f, 250f, 292f, 338f)
    for (a in angles) {
        val rad = Math.toRadians(a.toDouble())
        val sx = cx + (r * 0.42f * kotlin.math.cos(rad)).toFloat()
        val sy = cy - r * 0.04f + (r * 0.34f * kotlin.math.sin(rad)).toFloat()
        drawStar(sx, sy, smallR, emblem)
    }
    // 4) 天安门城楼（简化）
    val gw = r * 0.46f
    val gx0 = cx - gw / 2f
    val roofTop = cy + r * 0.04f
    val roofH = r * 0.10f
    val bodyH = r * 0.16f
    val bodyTop = roofTop + roofH
    val roof = Path().apply {
        moveTo(gx0 - r * 0.05f, bodyTop)
        lineTo(gx0 + gw + r * 0.05f, bodyTop)
        lineTo(gx0 + gw - r * 0.02f, roofTop)
        lineTo(gx0 + r * 0.02f, roofTop)
        close()
    }
    drawPath(roof, emblem)
    drawRect(emblem, Offset(gx0, bodyTop), Size(gw, bodyH))
    drawCircle(disc, r * 0.05f, Offset(cx, bodyTop + bodyH * 0.5f))
    drawCircle(disc, r * 0.032f, Offset(cx - r * 0.12f, bodyTop + bodyH * 0.5f))
    drawCircle(disc, r * 0.032f, Offset(cx + r * 0.12f, bodyTop + bodyH * 0.5f))
    drawRect(emblem, Offset(gx0 - r * 0.02f, bodyTop + bodyH), Size(gw + r * 0.04f, r * 0.10f))

    // 5) 麦穗（左右弧线）+ 齿轮底环
    for (side in listOf(-1f, 1f)) {
        val wp = Path().apply {
            moveTo(cx + side * r * 0.34f, cy + r * 0.42f)
            quadraticTo(cx + side * r * 0.58f, cy + r * 0.08f, cx + side * r * 0.32f, cy - r * 0.06f)
        }
        drawPath(wp, emblem, style = Stroke(width = r * 0.022f))
    }
    drawCircle(emblem, r * 0.30f, Offset(cx, cy + r * 0.06f), style = Stroke(width = r * 0.02f))

    // 6) 下方"中华人民共和国"
    val chnPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.13f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("中华人民共和国", cx, cy + r * 0.62f, chnPaint)

    // 7) 年份"2000"
    val yrPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.10f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("2000", cx, cy + r * 0.76f, yrPaint)
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

/**
 * 标准骰子点位布局，返回相对面中心的归一化坐标（单位 u）。
 * 黑点全部内缩到距面中心 70% 半径处（不再贴角贴边），参照用户提供的 3D 白骰子截图：
 * 即便是 4 号面的四个对角点、6 号面的 2x3 网格都明显有内缩 padding 让点居于面中部。
 */
private const val PIP_OFFSET = 0.7f

private fun dicePips(face: Int): List<Pair<Float, Float>> = when (face) {
    1 -> listOf(0f to 0f)
    2 -> listOf(-PIP_OFFSET to -PIP_OFFSET, PIP_OFFSET to PIP_OFFSET)
    3 -> listOf(-PIP_OFFSET to -PIP_OFFSET, 0f to 0f, PIP_OFFSET to PIP_OFFSET)
    4 -> listOf(
        -PIP_OFFSET to -PIP_OFFSET, -PIP_OFFSET to PIP_OFFSET,
        PIP_OFFSET to -PIP_OFFSET, PIP_OFFSET to PIP_OFFSET,
    )
    5 -> listOf(
        -PIP_OFFSET to -PIP_OFFSET, -PIP_OFFSET to PIP_OFFSET,
        0f to 0f,
        PIP_OFFSET to -PIP_OFFSET, PIP_OFFSET to PIP_OFFSET,
    )
    else -> listOf(
        -PIP_OFFSET to -PIP_OFFSET, -PIP_OFFSET to 0f, -PIP_OFFSET to PIP_OFFSET,
        PIP_OFFSET to -PIP_OFFSET, PIP_OFFSET to 0f, PIP_OFFSET to PIP_OFFSET,
    )
}
