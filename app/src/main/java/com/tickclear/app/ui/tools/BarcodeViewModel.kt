package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.tools.BarcodeTool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 条码识别界面状态。 */
sealed interface BarcodeUiState {
    object Idle : BarcodeUiState
    object Decoding : BarcodeUiState
    object NoBarcode : BarcodeUiState
    data class Decoded(
        val result: BarcodeTool.BarcodeResult,
        val product: BarcodeTool.ProductInfo? = null,
        val querying: Boolean = false,
        val queried: Boolean = false,
        /** true=网络层失败（提示可离线复制条码）；false + product==null=库中确实没有。 */
        val queryFailed: Boolean = false,
    ) : BarcodeUiState
}

/**
 * 条码识别 ViewModel（V2.9++）：解码在 IO 线程执行（ZXing core），
 * 商品查询走 OkHttp 调用公开 API，失败时优雅降级（仅展示条码号）。
 */
@HiltViewModel
class BarcodeViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Idle)
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

    fun decode(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = BarcodeUiState.Decoding
            val result = BarcodeTool.decode(bitmap)
            _state.value = if (result == null) BarcodeUiState.NoBarcode else BarcodeUiState.Decoded(result)
        }
    }

    fun query() {
        val cur = _state.value
        if (cur !is BarcodeUiState.Decoded) return
        if (cur.querying) return // 防重入：连点「查询」不重复发请求
        val token = cur.result.text
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = cur.copy(querying = true)
            val r = BarcodeTool.lookupProduct(token)
            // 请求期间用户可能已重新扫码 → 条码变了就丢弃这次迟到的结果，避免覆盖成陈旧数据
            val latest = _state.value
            if (latest !is BarcodeUiState.Decoded || latest.result.text != token) return@launch
            _state.value = when (r) {
                is BarcodeTool.LookupResult.Found ->
                    latest.copy(querying = false, product = r.info, queried = true, queryFailed = false)
                BarcodeTool.LookupResult.NotFound ->
                    latest.copy(querying = false, product = null, queried = true, queryFailed = false)
                BarcodeTool.LookupResult.Failed ->
                    latest.copy(querying = false, product = null, queried = true, queryFailed = true)
            }
        }
    }
}
