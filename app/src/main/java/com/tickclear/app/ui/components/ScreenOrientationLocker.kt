package com.tickclear.app.ui.components

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.tickclear.app.BuildConfig

/**
 * 进程级方向锁状态：用户「想要横屏」的意图放在这里，
 * 不依赖 Compose 的 rememberSaveable —— 在部分 ROM（如 HyperOS / 澎湃OS）上，
 * 旋转会触发 RulerScreen 整页移除重建，rememberSaveable 未能可靠恢复，
 * 导致 landscape 被重新初始化为 false、方向锁按竖屏施加而弹回竖屏。
 * 单例在进程生命周期内稳定存活，重建后从它恢复即可。
 */
object OrientationLockState {
    var desiredLandscape: Boolean = false
        private set

    fun setDesired(landscape: Boolean) {
        desiredLandscape = landscape
    }
}

/** 向上回溯宿主 Activity。抽出共享，避免各工具页重复定义。 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 锁定屏幕方向为横屏或竖屏，并带「旋转后自愈」兜底。
 *
 * 修复「点横屏瞬间被切回竖屏」（HyperOS / 澎湃OS 真机验证）：
 * 1) 用 SCREEN_ORIENTATION_SENSOR_LANDSCAPE / SENSOR_PORTRAIT（在目标朝向范围内跟随传感器），
 *    避免纯固定 LANDSCAPE/PORTRAIT 与 Android 12+「用户可覆盖应用固定方向」冲突被重置弹回。
 * 2) 关键自愈：注册 ComponentCallbacks.onConfigurationChanged，每次配置变更（旋转完成）后
 *    **以进程级单例的意图为准无条件重设**锁定方向。框架/部分 ROM 会在旋转后把 requestedOrientation
 *    悄悄重置，旧逻辑等「current != target」才纠正，但实测旋转完成时 current 已是 target、
 *    随后用户意图（landscape）又被整页重建清零 → 弹回竖屏；改为无条件重设后即便被重置也立刻纠正。
 * 3) 用户意图同步进 OrientationLockState 单例，整页重建后由调用方从单例恢复，方向不再丢失。
 * 4) 离开页面（DisposableEffect dispose）恢复 UNSPECIFIED，把方向交还系统。
 */
@Composable
fun LockScreenOrientation(landscape: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val target by rememberUpdatedState(
        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
    )

    // 同步用户意图到进程级单例（每帧重组都同步，确保与 landscape 实参一致；整页重建后可恢复）。
    OrientationLockState.setDesired(landscape)

    if (BuildConfig.DEBUG) {
        Log.d("OrientationLock", "apply landscape=$landscape -> ${targetString(target)}")
    }

    LaunchedEffect(landscape) {
        activity?.requestedOrientation = target
    }

    DisposableEffect(Unit) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val act = activity ?: return
                // 以单例意图为准无条件重设：旋转完成后框架/ROM 可能已把 requestedOrientation 悄悄重置，
                // 不等「!= target」再纠正（日志显示旋转完成时 current 已是 target，但随后意图被重建清零弹回）。
                val desired = OrientationLockState.desiredLandscape
                val desiredTarget = if (desired) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "OrientationLock",
                        "onConfigChanged current=${act.requestedOrientation} want=$desired target=$desiredTarget",
                    )
                }
                act.requestedOrientation = desiredTarget
            }

            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)
        onDispose {
            context.unregisterComponentCallbacks(callbacks)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (BuildConfig.DEBUG) Log.d("OrientationLock", "reset to UNSPECIFIED")
        }
    }
}

private fun targetString(o: Int): String = when (o) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "SENSOR_LANDSCAPE"
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "SENSOR_PORTRAIT"
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "UNSPECIFIED"
    else -> o.toString()
}
