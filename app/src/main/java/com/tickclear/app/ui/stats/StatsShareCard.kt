package com.tickclear.app.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.tickclear.app.R
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 统计页打卡分享卡：纯本地离屏 Canvas 绘制一张成就图（零新依赖），
 * 经 FileProvider 授权后通过系统分享 sheet 发出。配色由 Compose 主题经 toArgb() 传入，
 * 本对象不依赖 Compose 运行时，便于在事件回调中同步生成。
 */
data class CardColors(
    val bg: Int,
    val surface: Int,
    val primary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
)

object StatsShareCard {

    fun generate(context: Context, state: StatsUiState, colors: CardColors): Bitmap {
        val w = 1080
        val h = 1440
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply { color = colors.bg; isAntiAlias = true }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cardPad = 60f
        val cardPaint = Paint().apply { color = colors.surface; isAntiAlias = true }
        canvas.drawRoundRect(RectF(cardPad, cardPad, w - cardPad, h - cardPad), 48f, 48f, cardPaint)

        val titlePaint = Paint().apply {
            color = colors.primary
            textSize = 64f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint().apply {
            color = colors.onSurfaceVariant
            textSize = 34f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = colors.onSurface
            textSize = 88f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val accentPaint = Paint().apply {
            color = colors.primary
            textSize = 38f
            isAntiAlias = true
        }

        val left = cardPad + 60f
        var y = 200f
        canvas.drawText(context.getString(R.string.share_card_title), left, y, titlePaint)
        y += 70f
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        canvas.drawText(context.getString(R.string.share_card_subtitle, dateStr), left, y, labelPaint)
        y += 120f

        val items = listOf(
            context.getString(R.string.stats_streak) to
                context.getString(R.string.stats_streak_value, state.streakDays),
            context.getString(R.string.stats_total_completed) to "${state.totalCompleted}",
            context.getString(R.string.stats_today_rate) to
                "${(state.completionRate * 100).roundToInt()}%",
            context.getString(R.string.stats_longest_streak) to
                context.getString(R.string.stats_longest_streak_value, state.longestStreakDays),
        )
        val colW = (w - 2 * cardPad - 120f) / 2f
        items.chunked(2).forEach { row ->
            var x = left
            row.forEach { (label, value) ->
                canvas.drawText(value, x, y + 60f, valuePaint)
                canvas.drawText(label, x, y + 110f, labelPaint)
                x += colW
            }
            y += 210f
        }

        val medalCount = state.unlockedMedals.size
        canvas.drawText(context.getString(R.string.share_card_medals, medalCount), left, y + 60f, valuePaint)
        canvas.drawText(context.getString(R.string.share_card_medals_label), left, y + 110f, labelPaint)
        y += 210f

        canvas.drawText(
            context.getString(R.string.share_card_footer, context.getString(R.string.app_name)),
            left,
            h - cardPad - 50f,
            accentPaint,
        )
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
}
