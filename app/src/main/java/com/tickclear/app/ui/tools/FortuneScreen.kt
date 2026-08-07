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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate
import kotlin.random.Random

/** 一条趣味运势结果。纯娱乐，非预测。 */
private data class FortuneResult(
    val keyword: String,
    val luckyNum: Int,
    val colorName: String,
    val color: Color,
    val blessing: String,
    val index: Int, // 1..5 开心指数
)

/** 幸运色色值，顺序与 R.array.fortune_color_names 一一对应。 */
private val FORTUNE_COLOR_VALUES = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFEC407A), Color(0xFF26A69A),
)

/**
 * 今日运势（纯娱乐，无迷信，无预测）：依日期生成确定性结果，同一天一致；
 * 「再抽一次」仅用于好玩，会换一个随机结果，不代表任何真实走向。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortuneScreen(onBack: () -> Unit) {
    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    var seed by remember { mutableLongStateOf(todayEpoch) }

    val keywords = stringArrayResource(R.array.fortune_keywords)
    val colorNames = stringArrayResource(R.array.fortune_color_names)
    val blessings = stringArrayResource(R.array.fortune_blessings)

    val fortune = remember(seed, keywords, colorNames, blessings) {
        computeFortune(seed, keywords, colorNames, blessings)
    }

    val pop = remember { Animatable(1f) }
    LaunchedEffect(seed) {
        pop.snapTo(0.88f)
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 360f))
    }

    val keywordLabel = stringResource(R.string.fortune_keyword_label)
    val luckyNumLabel = stringResource(R.string.fortune_lucky_num_label)
    val luckyColorLabel = stringResource(R.string.fortune_lucky_color_label)
    val blessingLabel = stringResource(R.string.fortune_blessing_label)
    val indexLabel = stringResource(R.string.fortune_index_label)
    val indexValue = stringResource(R.string.fortune_index_value, fortune.index)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_fortune_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SimHintCard(stringResource(R.string.tools_fortune_hint))

            // 运势主卡：以幸运色做底色，关键词 / 幸运数 / 幸运色 / 星级 / 寄语集中呈现
            Card(
                modifier = Modifier.fillMaxWidth().scale(pop.value),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = fortune.color.copy(alpha = 0.14f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        fortune.keyword,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            contentDescription = "$keywordLabel ${fortune.keyword}"
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .semantics {
                                contentDescription = "$luckyNumLabel ${fortune.luckyNum}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            fillSphere(
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width / 2f,
                                base = fortune.color,
                            )
                        }
                        Text(
                            fortune.luckyNum.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "$luckyColorLabel ${fortune.colorName}"
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(fortune.color),
                        )
                        Text(fortune.colorName, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "$indexLabel $indexValue"
                        },
                    ) {
                        repeat(5) { i ->
                            Icon(
                                imageVector = if (i < fortune.index) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = fortune.color,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.fortune_blessing_quote, fortune.blessing),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "$blessingLabel ${fortune.blessing}"
                        },
                    )
                }
            }

            Text(
                stringResource(R.string.fortune_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { seed = todayEpoch + Random.nextLong(1, 100000) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                Text(stringResource(R.string.fortune_reroll))
            }
        }
    }
}



/** 由 seed 确定性地生成一条趣味运势。文案全部来自资源数组，色值按下标对齐。 */
private fun computeFortune(
    seed: Long,
    keywords: Array<String>,
    colorNames: Array<String>,
    blessings: Array<String>,
): FortuneResult {
    val r = Random(seed)
    val kw = keywords[r.nextInt(keywords.size)]
    val num = r.nextInt(1, 100)
    val colorIdx = r.nextInt(colorNames.size)
    val cName = colorNames[colorIdx]
    // 资源数组若与色值列表长度不一致，取模兜底避免越界
    val cColor = FORTUNE_COLOR_VALUES[colorIdx % FORTUNE_COLOR_VALUES.size]
    val blessing = blessings[r.nextInt(blessings.size)]
    val index = r.nextInt(1, 6)
    return FortuneResult(kw, num, cName, cColor, blessing, index)
}
