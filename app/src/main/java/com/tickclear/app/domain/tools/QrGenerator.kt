package com.tickclear.app.domain.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 二维码生成与保存（工具箱「二维码」工具，V2.9++）。
 * 引入 ZXing core（用户批准破例），仅用其编码器，Bitmap 自行绘制，零额外封装依赖。
 */
object QrGenerator {
    private const val WHITE = Color.WHITE
    private const val BLACK = Color.BLACK

    /**
     * 将文本编码为二维码 Bitmap（黑模块 / 白底）。内容为空返回 null。
     * [sizePx] 为目标像素边长；建议 ≥ 512 以保证扫码识别率。
     */
    fun generate(content: String, sizePx: Int, ecLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ecLevel,
                EncodeHintType.MARGIN to 2,
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) BLACK else WHITE)
                }
            }
            bitmap
        } catch (e: WriterException) {
            null
        }
    }

    /**
     * 保存二维码到系统相册（MediaStore）。Android Q+ 无需存储权限；
     * 返回是否成功（失败含权限/IO 异常均返回 false）。
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TickClear")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return@withContext false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
}
