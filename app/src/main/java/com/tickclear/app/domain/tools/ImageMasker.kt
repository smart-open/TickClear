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
import kotlin.math.hypot
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

    /** 涂抹形状：矩形框选（拖框）或自由笔刷（路径）。点坐标为归一化 [0,1]。 */
    sealed interface MaskShape {
        /** 矩形框选：(left, top, right, bottom) ∈ [0,1]，由 left ≤ right、top ≤ bottom。 */
        data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) : MaskShape

        /**
         * 自由笔刷：连续点列表 + 笔刷宽度（按 bitmap 短边的比例 ∈ (0,1]）。
         * 至少 2 个点。
         */
        data class Stroke(val points: List<Pair<Float, Float>>, val widthRatio: Float) : MaskShape {
            init {
                require(widthRatio > 0f) { "widthRatio must be > 0" }
            }
        }
    }

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
     * 对 [src] 应用遮挡（仅矩形框选），返回新位图（不修改原图）。
     * [rects] 为归一化选区；[strength] 控制马赛克强度（4..24，越大块越大越模糊）。
     *
     * 保留旧 API 以兼容去水印页（ImageRepair 类型不同，互不冲突）。
     */
    fun applyMask(
        src: Bitmap,
        rects: List<RectF>,
        mode: MaskMode,
        strength: Int,
    ): Bitmap = applyMaskWithShapes(
        src,
        rects.map { MaskShape.Box(it.left, it.top, it.right, it.bottom) },
        mode,
        strength,
    )

    /** 通用入口：矩形 + 笔刷混合。 */
    fun applyMaskWithShapes(
        src: Bitmap,
        shapes: List<MaskShape>,
        mode: MaskMode,
        strength: Int,
    ): Bitmap {
        if (shapes.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val paint = Paint().apply { isAntiAlias = false }
        val w = out.width
        val h = out.height
        val block = max(4, w / (strength.coerceIn(4, 24) * 4))

        for (shape in shapes) {
            when (shape) {
                is MaskShape.Box -> drawBox(out, canvas, paint, w, h, block, shape, mode)
                is MaskShape.Stroke -> drawStroke(canvas, paint, w, h, shape, mode, block, out)
            }
        }
        return out
    }

    private fun drawBox(
        bitmap: Bitmap,
        canvas: Canvas,
        paint: Paint,
        w: Int,
        h: Int,
        block: Int,
        box: MaskShape.Box,
        mode: MaskMode,
    ) {
        val left = (box.left * w).toInt().coerceIn(0, w - 1)
        val top = (box.top * h).toInt().coerceIn(0, h - 1)
        val right = (box.right * w).toInt().coerceIn(left + 1, w)
        val bottom = (box.bottom * h).toInt().coerceIn(top + 1, h)
        if (mode == MaskMode.BLACK) {
            paint.color = Color.BLACK
            canvas.drawRect(
                left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint,
            )
            return
        }
        var y = top
        while (y < bottom) {
            val yEnd = min(y + block, bottom)
            var x = left
            while (x < right) {
                val xEnd = min(x + block, right)
                paint.color = averageColor(bitmap, x, y, xEnd, yEnd)
                canvas.drawRect(
                    x.toFloat(), y.toFloat(), xEnd.toFloat(), yEnd.toFloat(), paint,
                )
                x += block
            }
            y += block
        }
    }

    /**
     * 笔刷渲染：把连续点拟合为线段，每段用矩形（与线段垂直方向各延伸半宽）拼接。
     * 段之间用三角形连接，避免折角处锯齿。
     *
     * 性能：MOSAIC 模式按段拆分为多个小矩形，逐块取平均色；BLACK 模式走
     * `Canvas.drawPath` 一气呵成。
     */
    private fun drawStroke(
        canvas: Canvas,
        paint: Paint,
        w: Int,
        h: Int,
        stroke: MaskShape.Stroke,
        mode: MaskMode,
        block: Int,
        src: Bitmap,
    ) {
        if (stroke.points.size < 2) return
        val shortSide = min(w, h)
        val halfPx = (stroke.widthRatio.coerceIn(0.005f, 0.5f) * shortSide) / 2f
        val px = stroke.points.map { (x, y) -> x.coerceIn(0f, 1f) * w to y.coerceIn(0f, 1f) * h }

        if (mode == MaskMode.BLACK) {
            // 直接用 Path 绘制一气呵成，性能最佳
            val path = android.graphics.Path()
            path.moveTo(px[0].first, px[0].second)
            for (i in 1 until px.size) path.lineTo(px[i].first, px[i].second)
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = halfPx * 2f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            return
        }

        // 笔刷模式 mosaic：每段用一连串方块近似（半宽范围内的方块平均色）
        drawStrokeMosaic(src, canvas, paint, w, h, block, halfPx, px)
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

    /**
     * 在源 [src] 上累加笔刷 MOSAIC：相邻点之间取若干小方块，以方块对应源区域的平均色覆盖。
     */
    private fun drawStrokeMosaic(
        src: Bitmap,
        canvas: Canvas,
        paint: Paint,
        w: Int,
        h: Int,
        block: Int,
        halfPx: Float,
        px: List<Pair<Float, Float>>,
    ) {
        for (i in 0 until px.size - 1) {
            val (ax, ay) = px[i]
            val (bx, by) = px[i + 1]
            val dx = bx - ax
            val dy = by - ay
            val len = hypot(dx, dy)
            if (len < 0.5f) continue
            // 段长 <= block 时整段一个采样点；否则按 block 切分
            val steps = max(1, (len / block).toInt())
            for (s in 0 until steps) {
                val t0 = s.toFloat() / steps
                val t1 = (s + 1f) / steps
                val cx = ax + dx * t0
                val cy = ay + dy * t0
                val cx1 = ax + dx * t1
                val cy1 = ay + dy * t1
                val left = (min(cx, cx1) - halfPx).toInt().coerceIn(0, w - 1)
                val top = (min(cy, cy1) - halfPx).toInt().coerceIn(0, h - 1)
                val right = (max(cx, cx1) + halfPx).toInt().coerceIn(left + 1, w)
                val bottom = (max(cy, cy1) + halfPx).toInt().coerceIn(top + 1, h)
                var y = top
                while (y < bottom) {
                    val yEnd = min(y + block, bottom)
                    var x = left
                    while (x < right) {
                        val xEnd = min(x + block, right)
                        paint.color = averageColor(src, x, y, xEnd, yEnd)
                        canvas.drawRect(
                            x.toFloat(), y.toFloat(), xEnd.toFloat(), yEnd.toFloat(), paint,
                        )
                        x += block
                    }
                    y += block
                }
            }
        }
    }
}