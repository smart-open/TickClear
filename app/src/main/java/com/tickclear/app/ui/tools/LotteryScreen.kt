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
                            .requiredHeight(56.dp),
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

/** 掷骰子动画：真 3D 立方体投影旋转 3s，期间不断翻面，结束定格为等概率随机点数。 */
@Composable
private fun DiceDisplay(token: Int, onResult: (Int) -> Unit) {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(1) }

    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val finalFace = Random.nextInt(1, 7)
        val spinX = launch {
            rotX.animateTo(rotX.value + 360f * 3f, tween(SPIN_DURATION, easing = LinearEasing))
        }
        val spinY = launch {
            rotY.animateTo(rotY.value + 360f * 3f, tween(SPIN_DURATION, easing = LinearEasing))
        }
        var elapsed = 0L
        while (elapsed < SPIN_DURATION) {
            shown = Random.nextInt(1, 7)
            delay(90)
            elapsed += 90
        }
        shown = finalFace
        spinX.join()
        spinY.join()
        onResult(finalFace)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(140.dp),
        ) {
            // 读取 rotX/rotY 的 State，每帧重绘投影立方体，实现真 3D 旋转
            draw3DDie(front = shown, rotXDeg = rotX.value, rotYDeg = rotY.value, size = size)
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

/** 3D 骰子：投影立方体，按 rotX/rotY 旋转后只绘制朝向相机（法线 z>0）的可见面。 */
private data class V3(val x: Float, val y: Float, val z: Float)

/** 给定正面点数，返回（顶面、右面）点数，保证不与正面或其对立面（和为 7）冲突。 */
private fun dieTopRight(front: Int): Pair<Int, Int> {
    val opp = 7 - front
    val cands = (1..6).filter { it != front && it != opp }
    return cands[0] to cands[1]
}

private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(
    a.x + (b.x - a.x) * t,
    a.y + (b.y - a.y) * t,
)

/** 在四边形 [TL,TR,BR,BL] 内做双线性插值，u/v ∈ [0,1]。 */
private fun quadPoint(tl: Offset, tr: Offset, br: Offset, bl: Offset, u: Float, v: Float): Offset {
    val top = lerpOffset(tl, tr, u)
    val bottom = lerpOffset(bl, br, u)
    return lerpOffset(top, bottom, v)
}

private fun DrawScope.draw3DDie(
    front: Int,
    rotXDeg: Float,
    rotYDeg: Float,
    size: Size,
) {
    val (topVal, rightVal) = dieTopRight(front)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val s = size.minDimension * 0.34f

    val base = listOf(
        V3(-s, -s, -s), V3(s, -s, -s), V3(s, s, -s), V3(-s, s, -s),
        V3(-s, -s, s), V3(s, -s, s), V3(s, s, s), V3(-s, s, s),
    )
    val ax = Math.toRadians(rotXDeg.toDouble())
    val ay = Math.toRadians(rotYDeg.toDouble())
    val ca = kotlin.math.cos(ax).toFloat()
    val sa = kotlin.math.sin(ax).toFloat()
    val cb = kotlin.math.cos(ay).toFloat()
    val sb = kotlin.math.sin(ay).toFloat()

    fun rotate(p: V3): V3 {
        val y1 = p.y * ca - p.z * sa
        val z1 = p.y * sa + p.z * ca
        val x2 = p.x * cb + z1 * sb
        val z2 = -p.x * sb + z1 * cb
        return V3(x2, y1, z2)
    }

    val rv = base.map { rotate(it) }
    val proj = rv.map { Offset(cx + it.x, cy + it.y) }

    // 面定义：4 个角索引按 [TL,TR,BR,BL] 环序，value 为该面点数（背面值为 7-正面）
    val faces = listOf(
        listOf(4, 5, 6, 7) to front,
        listOf(1, 2, 6, 5) to rightVal,
        listOf(0, 4, 7, 3) to (7 - rightVal),
        listOf(0, 1, 5, 4) to topVal,
        listOf(3, 2, 6, 7) to (7 - topVal),
        listOf(0, 1, 2, 3) to (7 - front),
    )

    drawSoftShadow(
        center = Offset(cx, cy + s * 1.15f),
        radiusX = s * 0.9f,
        radiusY = s * 0.3f,
        maxAlpha = 0.16f,
    )

    val light = V3(-0.5f, -0.5f, 0.7f)
    val llen = kotlin.math.sqrt(light.x * light.x + light.y * light.y + light.z * light.z)

    // 仅绘制法线朝向相机（z>0）的面，按 z 从远到近排序
    val visible = faces.mapNotNull { (idx, value) ->
        val vs = idx.map { rv[it] }
        val center = V3(
            (vs[0].x + vs[1].x + vs[2].x + vs[3].x) / 4f,
            (vs[0].y + vs[1].y + vs[2].y + vs[3].y) / 4f,
            (vs[0].z + vs[1].z + vs[2].z + vs[3].z) / 4f,
        )
        if (center.z <= 0.001f) null else idx to (value to center)
    }.sortedBy { it.second.second.z }

    for ((idx, valueCenter) in visible) {
        val (value, center) = valueCenter
        val nlen = kotlin.math.sqrt(center.x * center.x + center.y * center.y + center.z * center.z)
        val ndot = (center.x * light.x + center.y * light.y + center.z * light.z) / (nlen * llen)
        val shade = (0.55f + 0.45f * ndot.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val faceColor = Color(
            0.62f + 0.36f * shade,
            0.60f + 0.37f * shade,
            0.55f + 0.39f * shade,
        )
        val tl = proj[idx[0]]; val tr = proj[idx[1]]; val br = proj[idx[2]]; val bl = proj[idx[3]]
        val path = Path().apply {
            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
        }
        drawPath(path, faceColor)
        drawPath(path, Color(0xFFCFC9BE), style = Stroke(width = s * 0.035f))
        if (value in 1..6) {
            val pipR = s * 0.115f
            for ((px, py) in dicePips(value)) {
                val u = (px + 1f) / 2f
                val v = (py + 1f) / 2f
                val pt = quadPoint(tl, tr, br, bl, u, v)
                drawCircle(Color(0xFF2A2A2A), pipR, pt)
            }
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

/** 硬币：side=true 为正面（字：中国人民银行 / 1元 / 年号），false 为背面（国徽）。 */
private fun DrawScope.drawCoin(side: Boolean, size: Size) {
    drawCoinBody(size)
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    if (side) drawCoinObverse(cx, cy, r) else drawNationalEmblem(cx, cy, r)
}

/** 正面：顶部弧形国号 + 大号 1元 + 底部年号，参考 2020 版 1 元硬币。 */
private fun DrawScope.drawCoinObverse(cx: Float, cy: Float, r: Float) {
    val canvas = drawContext.canvas.nativeCanvas
    val dark = 0xFF3A3D42.toInt()
    // 顶部弧形：中国人民银行
    val oval = android.graphics.RectF(
        cx - r * 0.74f, cy - r * 0.74f,
        cx + r * 0.74f, cy + r * 0.74f,
    )
    val arcPath = android.graphics.Path().apply { arcTo(oval, 202f, 136f) }
    val arcPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.19f
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawTextOnPath("中国人民银行", arcPath, 0f, -r * 0.02f, arcPaint)
    // 中央 1元（1 大、元 小）
    val numPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.60f
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("1", cx - r * 0.22f, cy + r * 0.22f, numPaint)
    val yuanPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.40f
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("元", cx + r * 0.20f, cy + r * 0.16f, yuanPaint)
    // 底部年号
    val yrPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = dark
        textSize = r * 0.16f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("2020", cx, cy + r * 0.62f, yrPaint)
}

/** 背面：简化国徽——大星 + 四小星、天安门城楼、麦穗齿轮环。 */
private fun DrawScope.drawNationalEmblem(cx: Float, cy: Float, r: Float) {
    val emblem = Color(0xFF3A3D42)
    val disc = Color(0xFFC3C8CE)
    // 大星
    drawStar(cx, cy - r * 0.30f, r * 0.17f, emblem)
    // 四小星（环绕大星右下，指向大星）
    val smallR = r * 0.07f
    val angles = listOf(205f, 250f, 292f, 338f)
    for (a in angles) {
        val rad = Math.toRadians(a.toDouble())
        val sx = cx + (r * 0.42f * kotlin.math.cos(rad)).toFloat()
        val sy = cy - r * 0.04f + (r * 0.34f * kotlin.math.sin(rad)).toFloat()
        drawStar(sx, sy, smallR, emblem)
    }
    // 天安门城楼（简化）
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
    // 拱门（用底色挖空）
    drawCircle(disc, r * 0.05f, Offset(cx, bodyTop + bodyH * 0.5f))
    drawCircle(disc, r * 0.032f, Offset(cx - r * 0.12f, bodyTop + bodyH * 0.5f))
    drawCircle(disc, r * 0.032f, Offset(cx + r * 0.12f, bodyTop + bodyH * 0.5f))
    // 基座
    drawRect(emblem, Offset(gx0 - r * 0.02f, bodyTop + bodyH), Size(gw + r * 0.04f, r * 0.10f))
    // 麦穗（左右弧线）+ 齿轮底环
    for (side in listOf(-1f, 1f)) {
        val wp = Path().apply {
            moveTo(cx + side * r * 0.34f, cy + r * 0.42f)
            quadraticTo(cx + side * r * 0.58f, cy + r * 0.08f, cx + side * r * 0.32f, cy - r * 0.06f)
        }
        drawPath(wp, emblem, style = Stroke(width = r * 0.022f))
    }
    drawCircle(emblem, r * 0.30f, Offset(cx, cy + r * 0.06f), style = Stroke(width = r * 0.02f))
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
