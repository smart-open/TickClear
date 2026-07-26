package com.tickclear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** 勋章墙：展示全部勋章，已解锁彩色高亮，未解锁灰显 + 🔒；可点击查看详情，未解锁且可计算时显示进度。 */
@Composable
fun MedalWall(
    unlocked: Set<String>,
    modifier: Modifier = Modifier,
    onMedalClick: (Medal) -> Unit = {},
    progress: Map<String, MedalProgress> = emptyMap(),
) {
    val medals = MedalCatalog.ALL
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 88.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(medals, key = { it.key }) { medal ->
            MedalCell(
                medal = medal,
                isUnlocked = medal.key in unlocked,
                progress = progress[medal.key],
                onClick = onMedalClick,
            )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        // 未解锁且可计算进度时，展示 x/y 进度条
        if (!isUnlocked && progress != null && progress.target > 0 && progress.current >= 0) {
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
