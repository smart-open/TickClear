package com.tickclear.app.domain.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 图片涂抹遮挡工具（工具箱「马赛克」，V2.9++）。
 * 纯 Bitmap 像素处理，零额外依赖：支持马赛克（像素块平均）与涂黑两种遮挡方式。
 * 选区以归一化 [0,1] 矩形表示（相对原图），与显示尺寸解耦。
 */
object ImageMasker {

    /** 遮挡方式。 */
    enum class MaskMode { MOSAIC, BLACK }

    /** 从相册 Uri 载入位图（Android P+ 用 ImageDecoder，旧版回退 MediaStore.getBitmap）。 */
    suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(resolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(resolver, uri)
            }
            downscaleIfNeeded(src, maxSide = 1600)
        } catch (_: Exception) {
            null
        }
    }

    private fun downscaleIfNeeded(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val long = max(w, h)
        if (long <= maxSide) return src
        val scale = maxSide.toFloat() / long
        val tw = max(1, (w * scale).toInt())
        val th = max(1, (h * scale).toInt())
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }

    /**
     * 对 [src] 应用遮挡，返回新位图（不修改原图）。
     * [rects] 为归一化选区；[strength] 控制马赛克强度（4..24，越大块越大越模糊）。
     */
    fun applyMask(
        src: Bitmap,
        rects: List<RectF>,
        mode: MaskMode,
        strength: Int,
    ): Bitmap {
        if (rects.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val paint = Paint().apply { isAntiAlias = false }
        val w = out.width
        val h = out.height
        val block = max(4, w / (strength.coerceIn(4, 24) * 4))

        for (r in rects) {
            val left = (r.left * w).toInt().coerceIn(0, w - 1)
            val top = (r.top * h).toInt().coerceIn(0, h - 1)
            val right = (r.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (r.bottom * h).toInt().coerceIn(top + 1, h)
            if (mode == MaskMode.BLACK) {
                paint.color = Color.BLACK
                canvas.drawRect(
                    left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint,
                )
                continue
            }
            // 马赛克：按块取平均色再回填
            var y = top
            while (y < bottom) {
                val yEnd = min(y + block, bottom)
                var x = left
                while (x < right) {
                    val xEnd = min(x + block, right)
                    paint.color = averageColor(out, x, y, xEnd, yEnd)
                    canvas.drawRect(
                        x.toFloat(), y.toFloat(), xEnd.toFloat(), yEnd.toFloat(), paint,
                    )
                    x += block
                }
                y += block
            }
        }
        return out
    }

    private fun averageColor(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val p = bitmap.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += (p and 0xFF)
                count++
            }
        }
        if (count == 0) return Color.BLACK
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }
}
