package com.tickclear.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 统一短时提示：默认 3 秒后自动淡出消失。
 *
 * - 取代 SnackbarDuration.Short（Material3 固定 **4 秒**）带来的「挂太久」体感；
 * - 淡出由 SnackbarHost 默认 exit 转场（fadeOut + shrinkOut）负责，无需额外处理；
 * - 保留 action 语义：3 秒内点击 action 返回 [SnackbarResult.ActionPerformed]，
 *   超时或用户未点返回 [SnackbarResult.Dismissed]。
 *
 * Material3 的 [SnackbarDuration] 只提供 Short/Long/Indefinite，无法精确指定 3 秒，
 * 故用 Indefinite + 计时器在 [durationMillis] 后主动 dismiss 实现。
 *
 * @param durationMillis 自动消失时长，默认 3000ms。
 */
suspend fun SnackbarHostState.showTimedSnackbar(
    message: String,
    actionLabel: String? = null,
    durationMillis: Long = 3000L,
): SnackbarResult = coroutineScope {
    val autoDismiss = launch {
        delay(durationMillis)
        currentSnackbarData?.dismiss()
    }
    try {
        showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Indefinite,
        )
    } finally {
        autoDismiss.cancel()
    }
}
