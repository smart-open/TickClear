package com.tickclear.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tickclear.app.R

/** 今日完成率环：中心显示百分比。 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 8.dp,
) {
    val p = progress.coerceIn(0f, 1f)
    val pct = (p * 100).toInt()
    val ringContentDescription = stringResource(R.string.a11y_progress_ring, pct)
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = ringContentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { p },
            modifier = Modifier.size(size),
            strokeWidth = stroke,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
