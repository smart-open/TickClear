package com.tickclear.app.domain.tools

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 条码识别工具（工具箱「条码识别」，V2.9++）。
 * 复用已引入的 ZXing core 解码器（仅 core，零额外依赖）；商品查询走 OkHttp 调用
 * Open Food Facts 公开 API（免费、无需密钥），无网络或查不到时优雅降级为仅展示条码号。
 */
object BarcodeTool {

    /** 解码结果。 */
    data class BarcodeResult(val text: String, val format: String)

    /** 商品基础信息（查不到字段时为空）。 */
    data class ProductInfo(val name: String?, val brand: String?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * 从位图中解码条码/二维码。识别失败返回 null。
     * 注意：仅对整图做一次尝试，条码需基本正向清晰；复杂场景可多次旋转重试（本工具不做）。
     */
    fun decode(bitmap: Bitmap): BarcodeResult? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        return try {
            val result = reader.decode(binaryBitmap)
            val format = result.barcodeFormat ?: BarcodeFormat.CODE_128
            BarcodeResult(result.text, format.name)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 按条码号查询商品基础信息（Open Food Facts）。无网络/超时/无结果均返回 null。
     */
    suspend fun lookupProduct(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.optString("status") != "1") return@use null
                val product = json.optJSONObject("product") ?: return@use null
                val name = product.optString("product_name").takeIf { it.isNotBlank() }
                val brand = product.optString("brands").takeIf { it.isNotBlank() }
                ProductInfo(name, brand)
            }
        } catch (_: Exception) {
            null
        }
    }
}
