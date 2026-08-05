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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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

private val FORTUNE_KEYWORDS = listOf(
    "元气满满", "佛系一天", "锦鲤附体", "灵感爆发", "贵人相助",
    "适合摸鱼", "小确幸", "稳如老狗", "适合独处", "想躺平",
    "冲劲十足", "云淡风轻",
)

private val FORTUNE_COLORS = listOf(
    "中国红" to Color(0xFFE53935),
    "暖橙" to Color(0xFFFB8C00),
    "明黄" to Color(0xFFFDD835),
    "青草绿" to Color(0xFF43A047),
    "天空蓝" to Color(0xFF1E88E5),
    "梦幻紫" to Color(0xFF8E24AA),
    "蜜桃粉" to Color(0xFFEC407A),
    "薄荷青" to Color(0xFF26A69A),
)

private val FORTUNE_BLESSINGS = listOf(
    "今天也要对自己好一点，辛苦啦。",
    "别把小事放心上，开心最重要。",
    "运气藏在细节里，留意身边的小美好。",
    "累了就歇会儿，世界不会因为你停一秒而崩塌。",
    "今天适合做点让自己笑出来的事。",
    "你已经比昨天的自己更棒了一点。",
    "把期待调低一点，惊喜就会多一点。",
)

/**
 * 今日运势（纯娱乐，无迷信，无预测）：依日期生成确定性结果，同一天一致；
 * 「再抽一次」仅用于好玩，会换一个随机结果，不代表任何真实走向。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortuneScreen(onBack: () -> Unit) {
    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    var seed by remember { mutableStateOf(todayEpoch) }

    val fortune = remember(seed) { computeFortune(seed) }

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
                modifier = Modifier.fillMaxWidth(),
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
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(fortune.color),
                        contentAlignment = Alignment.Center,
                    ) {
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
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(fortune.color),
                        )
                        Text(fortune.colorName, style = MaterialTheme.typography.titleMedium)
                    }
                    Row {
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
                        "“${fortune.blessing}”",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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



/** 由 seed 确定性地生成一条趣味运势。 */
private fun computeFortune(seed: Long): FortuneResult {
    val r = Random(seed)
    val kw = FORTUNE_KEYWORDS[r.nextInt(FORTUNE_KEYWORDS.size)]
    val num = r.nextInt(1, 100)
    val (cName, cColor) = FORTUNE_COLORS[r.nextInt(FORTUNE_COLORS.size)]
    val blessing = FORTUNE_BLESSINGS[r.nextInt(FORTUNE_BLESSINGS.size)]
    val index = r.nextInt(1, 6)
    return FortuneResult(kw, num, cName, cColor, blessing, index)
}
