package com.tickclear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * GitHub 风格贡献热力图：按每日完成数着色。
 * completions: dateLocal(yyyy-MM-dd) -> 完成数。
 */
@Composable
fun HeatmapCalendar(
    completions: Map<String, Int>,
    modifier: Modifier = Modifier,
    weeks: Int = 18,
) {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val today = LocalDate.now()
    // 起点对齐到周一，向前推 (weeks-1) 周，保证整周网格
    val end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val start = end.minusWeeks((weeks - 1).toLong())

    val cell = 14.dp
    val gap = 3.dp

    Column(modifier) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            var weekStart = start
            while (!weekStart.isAfter(end)) {
                // 一周 7 行（周一..周日）
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in 0 until 7) {
                        val d = weekStart.plusDays(row.toLong())
                        val key = d.format(fmt)
                        val count = completions[key] ?: 0
                        val cellContentDescription = stringResource(R.string.a11y_heatmap_cell, key, count)
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colorForCount(count))
                                .semantics { contentDescription = cellContentDescription },
                        )
                    }
                }
                Spacer(Modifier.width(gap))
                weekStart = weekStart.plusWeeks(1)
            }
        }
        Spacer(Modifier.height(8.dp))
        // 图例
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.heatmap_less), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            (0..4).forEach { level ->
                Box(
                    Modifier
                        .size(cell)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorForLevel(level)),
                )
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.heatmap_more), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun colorForCount(count: Int): Color {
    val level = when {
        count <= 0 -> 0
        count == 1 -> 1
        count <= 3 -> 2
        count <= 5 -> 3
        else -> 4
    }
    return colorForLevel(level)
}

@Composable
private fun colorForLevel(level: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        0 -> scheme.surfaceVariant
        1 -> scheme.primary.copy(alpha = 0.3f)
        2 -> scheme.primary.copy(alpha = 0.55f)
        3 -> scheme.primary.copy(alpha = 0.8f)
        else -> scheme.primary
    }
}
