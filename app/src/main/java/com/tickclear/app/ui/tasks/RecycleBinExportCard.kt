package com.tickclear.app.ui.tasks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.tickclear.app.R
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.ui.stats.CardColors
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回收站清单导出卡：纯本地离屏 Canvas 绘制一张 PNG（零新依赖），
 * 经 FileProvider 授权后通过系统分享 sheet 发出。高度随条目数自适应。
 * 配色由 Compose 主题经 toArgb() 传入，不依赖 Compose 运行时。
 */
object RecycleBinExportCard {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun generate(context: Context, items: List<RecycleBinItem>, colors: CardColors): Bitmap {
        val w = 1080
        val headerH = 360f
        val rowH = 130f
        val footerH = 140f
        val contentH = if (items.isEmpty()) 200f else items.size * rowH
        val h = (headerH + contentH + footerH).roundToInt()
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
        val namePaint = Paint().apply {
            color = colors.onSurface
            textSize = 46f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val typePaint = Paint().apply {
            color = colors.onSurfaceVariant
            textSize = 32f
            isAntiAlias = true
        }
        val accentPaint = Paint().apply {
            color = colors.primary
            textSize = 34f
            isAntiAlias = true
        }

        val left = cardPad + 60f
        var y = cardPad + 120f
        canvas.drawText(context.getString(R.string.recycle_bin_export_title), left, y, titlePaint)
        y += 64f
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
        canvas.drawText(context.getString(R.string.recycle_bin_export_subtitle, nowStr), left, y, labelPaint)

        if (items.isEmpty()) {
            y += 200f
            canvas.drawText(context.getString(R.string.recycle_bin_export_empty), left, y, namePaint)
        } else {
            y += 120f
            items.forEach { item ->
                val name = item.name.ifEmpty { context.getString(R.string.recycle_bin_unnamed) }
                val typeStr = context.getString(
                    if (item.type == "task") R.string.recycle_bin_export_item_task else R.string.recycle_bin_export_item_group,
                    dateFmt.format(Date(item.deletedAt)),
                )
                canvas.drawText(name, left, y + 44f, namePaint)
                canvas.drawText(typeStr, left, y + 96f, typePaint)
                y += rowH
            }
        }

        canvas.drawText(
            context.getString(R.string.recycle_bin_export_chooser),
            left,
            h - cardPad - 50f,
            accentPaint,
        )
        return bitmap
    }

    /** 将 Bitmap 存入 cacheDir/share 并经 FileProvider 授权分享。 */
    fun share(context: Context, bitmap: Bitmap, fileName: String = "recycle_bin_${System.currentTimeMillis()}.png") {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "com.tickclear.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.recycle_bin_export_chooser)))
    }
}
