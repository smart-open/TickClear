package com.tickclear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.model.Medal
import com.tickclear.app.domain.model.MedalCatalog
import com.tickclear.app.domain.model.MedalProgress

/**
 * 勋章墙：展示全部勋章，已解锁彩色高亮，未解锁灰显 + 🔒；可点击查看详情，未解锁且可计算时显示进度。
 *
 * 实现说明：勋章总数固定且很少，用非 Lazy 的 chunked 行网格渲染。
 * ⚠️ 切勿改回 LazyVerticalGrid——本组件被 StatsScreen 的 Column(verticalScroll) 包裹，
 * Lazy 组件在无限高度约束下测量会抛 IllegalStateException 直接崩溃（即使 userScrollEnabled=false）。
 */
@Composable
fun MedalWall(
    unlocked: Set<String>,
    modifier: Modifier = Modifier,
    onMedalClick: (Medal) -> Unit = {},
    progress: Map<String, MedalProgress> = emptyMap(),
) {
    val medals = MedalCatalog.ALL
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 模拟 GridCells.Adaptive(minSize = 88.dp)：按可用宽度自适应列数
        val spacing = 8.dp
        val columns = ((maxWidth + spacing) / (88.dp + spacing)).toInt().coerceAtLeast(1)
        // V2.8X：每行 cell 等高——靠 MedalCell 内部统一保留进度区固定高度实现，
        // 避免解锁/未解锁 cell 内部多出的 LinearProgressIndicator+Text 把行末 cell 顶高（已移除 IntrinsicSize 依赖）。
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            medals.chunked(columns).forEach { rowMedals ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    rowMedals.forEach { medal ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            MedalCell(
                                medal = medal,
                                isUnlocked = medal.key in unlocked,
                                progress = progress[medal.key],
                                onClick = onMedalClick,
                            )
                        }
                    }
                    // 末行补空位，保持各格等宽
                    repeat(columns - rowMedals.size) { Spacer(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun MedalCell(
    medal: Medal,
    isUnlocked: Boolean,
    progress: MedalProgress?,
    onClick: (Medal) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (isUnlocked) scheme.primaryContainer else scheme.surfaceVariant
    val content = if (isUnlocked) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val statusText = stringResource(
        if (isUnlocked) R.string.a11y_medal_unlocked else R.string.a11y_medal_locked,
    )
    val medalContentDescription = stringResource(R.string.a11y_medal, stringResource(medal.nameRes), statusText)

    // 解锁脉冲：仅在「未解锁→已解锁」状态转换本会话内触发一次，初次进入已解锁态不闪。
    val scale = remember { Animatable(1f) }
    var wasUnlocked by remember { mutableStateOf(isUnlocked) }
    LaunchedEffect(isUnlocked) {
        if (isUnlocked && !wasUnlocked) {
            scale.snapTo(1f)
            scale.animateTo(1.15f, tween(durationMillis = 130))
            scale.animateTo(1f, tween(durationMillis = 180))
        }
        wasUnlocked = isUnlocked
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = { onClick(medal) })
            .semantics { contentDescription = medalContentDescription }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isUnlocked) medal.icon else "🔒",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(medal.nameRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        // 统一高度：无论是否解锁都保留进度区固定高度，避免同行星章 cell 高度不一致（无需 IntrinsicSize）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!isUnlocked && progress != null && progress.target > 0 && progress.current >= 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val frac = (progress.current.toFloat() / progress.target).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.medal_progress, progress.current, progress.target),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
