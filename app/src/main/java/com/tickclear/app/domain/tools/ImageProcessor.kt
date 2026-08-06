package com.tickclear.app.domain.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 图片处理工具（工具箱「图片压缩 / 图片黑白」，V2.9++）。
 * 纯 Bitmap 处理，零额外依赖，全部本地完成不上传服务器。
 */
object ImageProcessor {

    /** 从相册 Uri 载入位图，按 maxSide 限制长边（防 OOM）。 */
    suspend fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 4096): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(resolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { dec, _, _ ->
                        dec.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(resolver, uri)
                }
                downscale(src, maxSide)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 按 maxSide 等比缩小。缩放发生时必须立即 [Bitmap.recycle] 原图：
     * 全分辨率解码结果（手机主摄轻松 4000×3000，约 48MB ARGB_8888）在返回后仅靠 GC 回收，
     * 与缩放图、后续处理副本并存会让 Native 内存峰值翻倍，低内存机型连续处理大图时会 OOM。
     */
    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxSide) return src
        val scale = maxSide.toFloat() / long
        val out = Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * scale).toInt()),
            max(1, (src.height * scale).toInt()),
            true,
        )
        if (out !== src) src.recycle()
        return out
    }

    /** 按最大边长等比缩放（不放大）。 */
    fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxSide) return src
        val scale = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * scale).toInt()),
            max(1, (src.height * scale).toInt()),
            true,
        )
    }

    /** 灰度（去饱和）。 */
    fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(
            src.width,
            src.height,
            src.config ?: Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(out)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** 黑白（二值化）：luminance >= threshold 判白，否则黑。 */
    fun toBlackWhite(src: Bitmap, threshold: Int): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            px[i] = if (lum >= threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** 压缩为字节数组。 */
    fun compress(src: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val os = ByteArrayOutputStream()
        src.compress(format, quality.coerceIn(1, 100), os)
        return os.toByteArray()
    }

    /** 原始文件大小（字节），用于压缩前后对比；取不到返回 null。 */
    suspend fun fileSizeFromUri(context: Context, uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) return@withContext c.getLong(0)
                }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 保存到相册：API29+ 用 MediaStore（免存储权限），旧版落应用 Pictures 目录。
     * 返回保存位置描述，失败返回 null。
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        displayName: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val mime = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TickClear")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@withContext null
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    bitmap.compress(format, quality, os)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                "Pictures/TickClear/$displayName.$ext"
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "TickClear",
                )
                dir.mkdirs()
                val file = java.io.File(dir, "$displayName.$ext")
                java.io.FileOutputStream(file).use { bitmap.compress(format, quality, it) }
                file.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}
