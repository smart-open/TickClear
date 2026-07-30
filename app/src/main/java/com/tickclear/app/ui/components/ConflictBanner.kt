package com.tickclear.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import com.tickclear.app.ui.theme.Warning

/** 今日时间冲突横幅：存在冲突项时显示。 */
@Composable
fun ConflictBanner(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 中性浅底（不刺眼），底部叠一条淡黄强调条（参 ConflictBanner 设计）。
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Spacing.xs,
    ) {
        Box {
            Row(
                // 高度收敛：上下内边距 4dp（明显小于旧版），图标小号，文字 labelMedium。
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    // 弱化为中性灰（onSurfaceVariant），降低原 error 红标的视觉冲击，保留语义。
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.today_conflict_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            // 淡黄色底部强调条（amber 20% 透明，深浅主题均柔和）。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .height(2.dp)
                    .background(Warning.copy(alpha = 0.2f)),
            )
        }
    }
}
