package com.tickclear.app.ui.stats

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tickclear.app.R
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 统计页打卡分享卡：本地渲染完整统计页为一张长图（零新依赖），
 * 经 FileProvider 授权后通过系统分享 sheet 发出。
 * 配色由 Compose 主题经 toArgb() 传入，生成时使用 ComposeView 离屏测量绘制，
 * 复用 StatsOverviewColumn / StatsDetailColumn，确保图片包含整个页面的全部信息。
 */
data class CardColors(
    val bg: Int,
    val surface: Int,
    val primary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
)

object StatsShareCard {

    fun generate(
        context: Context,
        state: StatsUiState,
        period: StatsPeriod,
        trend: List<TrendBucket>,
        colors: CardColors,
    ): Bitmap {
        val widthPx = 1080
        val density = context.resources.displayMetrics.density
        val widthDp = (widthPx / density).dp
        val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val composeView = ComposeView(context).apply {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    MaterialTheme(colorScheme = remember { colorSchemeFromCard(colors, isDark) }) {
                        StatsShareContent(
                            state = state,
                            period = period,
                            trend = trend,
                            modifier = Modifier.width(widthDp),
                        )
                    }
                }
            }
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        val measuredWidth = composeView.measuredWidth.coerceAtLeast(1)
        val measuredHeight = composeView.measuredHeight.coerceAtLeast(1)
        composeView.layout(0, 0, measuredWidth, measuredHeight)

        val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)
        return bitmap
    }

    /** 将 Bitmap 存入 cacheDir/share 并经 FileProvider 授权分享。 */
    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "stats_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "com.tickclear.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_card_chooser)))
    }

    private fun colorSchemeFromCard(colors: CardColors, dark: Boolean): ColorScheme {
        val bg = Color(colors.bg)
        val surface = Color(colors.surface)
        val primary = Color(colors.primary)
        val onSurface = Color(colors.onSurface)
        val onSurfaceVariant = Color(colors.onSurfaceVariant)
        return if (dark) {
            darkColorScheme(
                background = bg,
                surface = surface,
                surfaceVariant = surface.copy(alpha = 0.85f),
                primary = primary,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                primaryContainer = primary.copy(alpha = 0.25f),
                onPrimaryContainer = primary,
            )
        } else {
            lightColorScheme(
                background = bg,
                surface = surface,
                surfaceVariant = surface.copy(alpha = 0.92f),
                primary = primary,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                primaryContainer = primary.copy(alpha = 0.15f),
                onPrimaryContainer = primary,
            )
        }
    }
}

/**
 * 分享卡完整内容：顶部标题 + 概览栏 + 明细栏（趋势/热力图/分组/勋章）+ 底部 footer。
 * 不滚动，离屏测量时展开为完整高度。
 */
@Composable
private fun StatsShareContent(
    state: StatsUiState,
    period: StatsPeriod,
    trend: List<TrendBucket>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.share_card_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            Text(
                text = stringResource(R.string.share_card_subtitle, dateStr),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatsOverviewColumn(state = state)
        StatsDetailColumn(
            state = state,
            period = period,
            trend = trend,
            onPeriodChange = {},
            onMedalClick = {},
        )

        Text(
            text = stringResource(R.string.share_card_footer, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
