package com.tickclear.app.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

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

/**
 * 掷骰子动画：真 3D 立方体高速翻滚 + 三段弹跳落地，3s 后减速停稳，
 * **朝上那一面**即等概率随机结果（与真实骰子一致）。
 *
 * 时间轴由单个线性 progress（0→1）驱动，旋转与弹跳各自套不同曲线：
 * 旋转走 [DecelEasing] 能量衰减，弹跳走 [diceHop] 递减抛物线，两者互不干扰。
 */
@Composable
private fun DiceDisplay(
    token: Int,
    onAnimatingChange: (Boolean) -> Unit,
    onResult: (Int) -> Unit,
) {
    var shown by remember { mutableIntStateOf(1) }
    // 落定时额外绕 Y 轴转的 1/4 圈数：让每次停下的侧面朝向不同，朝上的面不受影响。
    var quarterTurns by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        onAnimatingChange(true)
        val finalFace = Random.nextInt(1, 7)
        try {
            // 骰子在整段翻滚中就是同一颗（点数分布固定），靠高速旋转制造悬念——
            // 不再逐帧随机换点数，避免"面上数字乱闪"的廉价感。
            shown = finalFace
            quarterTurns = Random.nextInt(4)
            // 必须先归零：Animatable 停在 1f 时再 animateTo(1f) 目标等于当前值，
            // 动画会瞬间完成，表现为"第二次点击骰子不转"。
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SPIN_DURATION, easing = LinearEasing))
        } finally {
            onAnimatingChange(false)
        }
        onResult(finalFace)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(176.dp),
        ) {
            val p = progress.value
            val spun = DecelEasing.transform(p)
            // 绕 X 转 3 整圈、绕 Y 转 (5 + n/4) 圈：X 为整圈保证朝上的面回到顶部，
            // Y 的 1/4 圈只是水平转向，不改变谁朝上。
            val rx = spun * (6f * Math.PI.toFloat())
            val ry = spun * ((10f + quarterTurns * 0.5f) * Math.PI.toFloat())
            draw3DDie(shown, size, rx, ry, lift = diceHop(p))
        }
    }
}

/**
 * 抛掷减速曲线（power-out）：起手极快、末段柔和收住，模拟能量衰减。
 * 匀速旋转到点突然静止会很"机械"，这条曲线让骰子/硬币像是自己慢慢停下的。
 */
private val DecelEasing: Easing = Easing { p -> 1f - (1f - p).pow(2.6f) }

/**
 * 骰子弹跳高度（0=贴地，1=最高点），输入为线性进度。
 * 四段递减抛物线模拟"抛起→落地→越弹越低→停住"，最后 10% 时间已完全落地，
 * 与旋转的末段微调重叠，收尾自然。
 */
private fun diceHop(p: Float): Float {
    fun arc(x: Float, h: Float) = 4f * h * x * (1f - x)
    return when {
        p < 0.36f -> arc(p / 0.36f, 1f)
        p < 0.62f -> arc((p - 0.36f) / 0.26f, 0.40f)
        p < 0.80f -> arc((p - 0.62f) / 0.18f, 0.15f)
        p < 0.90f -> arc((p - 0.80f) / 0.10f, 0.05f)
        else -> 0f
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
            // animateTo 本身就会挂起到动画结束，无需再起协程 + 空转轮询计时
            flip.animateTo(flip.value + 360f * 6f, tween(SPIN_DURATION, easing = DecelEasing))
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

// ---------------------------------------------------------------------------
// 3D 骰子（等距投影立方体）
// 立方体几何、圆角切角、渐变受光、背面剔除与深度排序全部走共享的 drawCube3D
// （见 IllustrationKit.kt），这里只保留骰子专属部分：
// 各面点数、各面基色、点位分布与黑点画法。
// ---------------------------------------------------------------------------

/**
 * 给定**朝上**的点数 [value]，返回 6 个面的点数。
 *
 * 结果面放 [CubeFace.Top] 而非 Front：等距投影下 Front 是左下方那个菱形侧面，
 * 既不朝上也不正对镜头，把结果画在那里会让人分不清"到底哪面是结果"。
 * 真实骰子的读数就是朝上那面，Top 又恰好是等距视图里最亮、位置最高的面。
 *
 * 面序遵循标准西式**右手骰**：1、2、3 绕公共顶点逆时针（等价于法线满足 n₁×n₂=n₃），
 * 且对面和恒为 7，因此翻滚到任意角度露出的可见面组合都合法自洽。
 */
private fun dieFaceLabels(value: Int): Map<CubeFace, Int> {
    val front = when (value) { 1 -> 2; 2 -> 6; 3 -> 2; 4 -> 2; 5 -> 1; else -> 5 }
    val right = when (value) { 1 -> 3; 2 -> 3; 3 -> 6; 4 -> 1; 5 -> 3; else -> 3 }
    return mapOf(
        CubeFace.Top to value,
        CubeFace.Front to front,
        CubeFace.Right to right,
        CubeFace.Bottom to 7 - value,
        CubeFace.Back to 7 - front,
        CubeFace.Left to 7 - right,
    )
}

/** 各面基色：顶受光最亮、前次之、右/左更暗、底/后最暗，形成统一左上光源体积感。 */
private fun dieFaceColor(face: CubeFace): Color = when (face) {
    CubeFace.Top -> Color(0xFFFBF7EF)
    CubeFace.Front -> Color(0xFFF3ECDC)
    CubeFace.Right, CubeFace.Left -> Color(0xFFE7DBC4)
    CubeFace.Back -> Color(0xFFD9CCB2)
    CubeFace.Bottom -> Color(0xFFCFC0A4)
}

/** 单面点位（归一化到 [-o,o]，o=0.55 内缩不贴边）。 */
private fun diePipVecs(face: Int): List<Pair<Float, Float>> {
    val o = 0.55f
    return when (face) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-o to -o, o to o)
        3 -> listOf(-o to -o, 0f to 0f, o to o)
        4 -> listOf(-o to -o, -o to o, o to -o, o to o)
        5 -> listOf(-o to -o, -o to o, 0f to 0f, o to -o, o to o)
        else -> listOf(-o to -o, -o to 0f, -o to o, o to -o, o to 0f, o to o)
    }
}

/**
 * 绘制真 3D 骰子：按 [rotX]/[rotY] 翻滚的圆角受光立方体，
 * 各面点数由 [drawCube3D] 的面内容回调绘制，随几何一起旋转，永远贴在面上。
 *
 * [lift] 为离地高度（0=贴地，1=弹跳最高点）。阴影**不跟着骰子飞**——它固定投在地面，
 * 只随高度收缩变淡，这样弹跳才有真正的空间感而不是整体平移。
 */
private fun DrawScope.draw3DDie(
    value: Int,
    size: Size,
    rotX: Float = 0f,
    rotY: Float = 0f,
    lift: Float = 0f,
) {
    val scale = size.minDimension * 0.235f
    val restY = size.height / 2f
    val center = Offset(size.width / 2f, restY - scale * 1.05f * lift)
    val labels = dieFaceLabels(value)
    val pipR = scale * 0.135f

    // 地面阴影：始终贴地，骰子越高越小越淡
    drawSoftShadow(
        center = Offset(size.width / 2f, restY + scale * 1.45f),
        radiusX = scale * 1.5f * (1f - 0.32f * lift),
        radiusY = scale * 0.5f * (1f - 0.32f * lift),
        maxAlpha = 0.22f * (1f - 0.55f * lift),
    )

    drawCube3D(
        center = center,
        scale = scale,
        shadow = false,
        rotX = rotX,
        rotY = rotY,
        faceColor = ::dieFaceColor,
        edgeColor = Color(0xFFB9AB8D),
        softEdgeColor = Color(0xFF786C54).copy(alpha = 0.35f),
    ) { face, toScreen ->
        // 黑点：暗点 + 右下投影 + 左上高光小点，呈内凹感
        for ((u, v) in diePipVecs(labels[face] ?: 1)) {
            val off = toScreen(u, v)
            drawCircle(
                Color(0xFF3A3A3A).copy(alpha = 0.26f),
                pipR * 1.04f,
                Offset(off.x + pipR * 0.16f, off.y + pipR * 0.20f),
            )
            drawCircle(Color(0xFF2A2A2A), pipR, off)
            drawCircle(
                Color(0xFFFFFFFF).copy(alpha = 0.5f),
                pipR * 0.3f,
                Offset(off.x - pipR * 0.28f, off.y - pipR * 0.30f),
            )
        }
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
 * 旧版 2D 平面骰子的点位布局（`dicePips`/`PIP_OFFSET`）已废弃，
 * 真 3D 立方体改用上方的 [diePipVecs]（点位归一化到 [-o,o]，o=0.55）。
 */
