package com.tickclear.app.domain.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * 图片去水印工具（工具箱「去水印」，V2.9++，简易版）。
 * 零额外依赖，纯 Bitmap 像素处理：
 *  - [RepairMode.REPAIR] 色彩修复：用选区四周环带的平均色覆盖选区，
 *    对纯色/接近纯色背景上的水印、AI 生成文字水印效果最佳。
 *  - [RepairMode.BLUR]   模糊柔化：对选区做轻度缩放模糊，弱化半透明或复杂背景上的水印。
 * 选区以归一化 [0,1] 矩形表示，与显示尺寸解耦（同 [ImageMasker]）。
 */
object ImageRepair {

    /** 修复方式。 */
    enum class RepairMode { REPAIR, BLUR }

    /**
     * 按 [mode] 对 [src] 应用去水印，返回新位图（不修改原图）。
     * [rects] 为归一化选区；[strength] 控制修复强度（4..16）。
     */
    fun applyRepair(
        src: Bitmap,
        rects: List<RectF>,
        mode: RepairMode,
        strength: Int,
    ): Bitmap {
        if (rects.isEmpty()) return src
        return when (mode) {
            RepairMode.REPAIR -> repairBySurrounding(src, rects, marginPx = (strength.coerceIn(4, 16)) * 2)
            RepairMode.BLUR -> blurRegion(src, rects, strength = strength.coerceIn(4, 16))
        }
    }

    /**
     * 色彩修复：用选区四周一圈（[marginPx] 宽）像素的平均色填充选区。
     * 若选区铺满整图导致无环带可取，则回退为全图平均色填充。
     */
    fun repairBySurrounding(src: Bitmap, rects: List<RectF>, marginPx: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val w = out.width
        val h = out.height
        val m = marginPx.coerceAtLeast(1)

        for (r in rects) {
            val left = (r.left * w).toInt().coerceIn(0, w - 1)
            val top = (r.top * h).toInt().coerceIn(0, h - 1)
            val right = (r.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (r.bottom * h).toInt().coerceIn(top + 1, h)

            var rr = 0L
            var gg = 0L
            var bb = 0L
            var count = 0
            val x0 = (left - m).coerceAtLeast(0)
            val x1 = (right + m).coerceAtMost(w)
            val y0 = (top - m).coerceAtLeast(0)
            val y1 = (bottom + m).coerceAtMost(h)
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val onRing = x < left || x >= right || y < top || y >= bottom
                    if (!onRing) continue
                    val p = out.getPixel(x, y)
                    rr += (p shr 16) and 0xFF
                    gg += (p shr 8) and 0xFF
                    bb += p and 0xFF
                    count++
                }
            }
            if (count == 0) {
                // 选区铺满全图：回退为整图平均色
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val p = out.getPixel(x, y)
                        rr += (p shr 16) and 0xFF
                        gg += (p shr 8) and 0xFF
                        bb += p and 0xFF
                        count++
                    }
                }
            }
            if (count == 0) continue
            val color = Color.rgb((rr / count).toInt(), (gg / count).toInt(), (bb / count).toInt())
            val paint = Paint().apply { this.color = color }
            Canvas(out).drawRect(
                left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint,
            )
        }
        return out
    }

    /**
     * 模糊柔化：对选区做轻度缩放模糊——裁出选区、缩到很小再放大回原尺寸（双线性），
     * 得到平滑柔化效果，弱化半透明/复杂背景上的水印。[strength] 越大越模糊（缩放更小）。
     */
    fun blurRegion(src: Bitmap, rects: List<RectF>, strength: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val w = out.width
        val h = out.height
        // strength 4..16 → 缩放比 0.40..0.06（越大越糊）
        val scale = (0.46f - (strength.coerceIn(4, 16) - 4) * 0.032f).coerceIn(0.06f, 0.5f)
        val paint = Paint().apply {
            isAntiAlias = true
        }
        for (r in rects) {
            val left = (r.left * w).toInt().coerceIn(0, w - 1)
            val top = (r.top * h).toInt().coerceIn(0, h - 1)
            val right = (r.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (r.bottom * h).toInt().coerceIn(top + 1, h)
            val rw = right - left
            val rh = bottom - top
            if (rw <= 0 || rh <= 0) continue
            val crop = runCatching { Bitmap.createBitmap(out, left, top, rw, rh) }.getOrNull() ?: continue
            val sw = maxOf(1, (rw * scale).toInt())
            val sh = maxOf(1, (rh * scale).toInt())
            val small = Bitmap.createScaledBitmap(crop, sw, sh, true)
            Canvas(out).drawBitmap(
                small, null, Rect(left, top, right, bottom), paint,
            )
            // 注意：crop 与 out 共享像素缓冲区，不可 recycle，否则会破坏 out。
            small.recycle()
        }
        return out
    }
}
