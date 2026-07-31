package com.tickclear.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import com.tickclear.app.ui.theme.Warning

/** 今日时间冲突横幅：存在冲突项时显示。V2.8X++：淡黄底 + 浅黄警告图标 + 无下边框 + 高度加倍。 */
@Composable
fun ConflictBanner(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 淡底色：浅黄低透明，既是警告色又柔和（深浅主题均不刺眼）。
        color = Warning.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Spacing.xs,
    ) {
        Row(
            // 高度扩大一倍（上下内边距 4dp → 8dp，Spacing.sm），图标放大，文字 labelMedium。
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                // 浅黄色警告图标（与淡黄底呼应）。
                tint = Warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.today_conflict_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}
