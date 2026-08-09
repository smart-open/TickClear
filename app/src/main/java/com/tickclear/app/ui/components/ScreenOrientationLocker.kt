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
 * 修复「点横屏瞬间被切回竖屏」：
 * 1) 用 SCREEN_ORIENTATION_SENSOR_LANDSCAPE / SENSOR_PORTRAIT（在目标朝向范围内跟随传感器），
 *    避免纯固定 LANDSCAPE/PORTRAIT 与 Android 12+「用户可覆盖应用固定方向」冲突被重置弹回。
 * 2) 关键自愈：注册 ComponentCallbacks.onConfigurationChanged，每次配置变更（旋转完成）后
 *    重新断言锁定方向。若框架/部分 ROM 在旋转后把 requestedOrientation 重置，回调里立刻纠正，
 *    避免「切过去又切回来」。仅当期望横屏且当前被改回时才重设（幂等，不会抖动）。
 * 3) 离开页面（DisposableEffect dispose）恢复 UNSPECIFIED，把方向交还系统。
 */
@Composable
fun LockScreenOrientation(landscape: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val target by rememberUpdatedState(
        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
    )
    val wantLandscape by rememberUpdatedState(landscape)

    if (BuildConfig.DEBUG) {
        Log.d("OrientationLock", "apply landscape=$landscape -> ${targetString(target)}")
    }

    LaunchedEffect(landscape) {
        activity?.requestedOrientation = target
    }

    DisposableEffect(Unit) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val act = activity
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "OrientationLock",
                        "onConfigChanged current=${act?.requestedOrientation} want=$wantLandscape target=$target",
                    )
                }
                if (wantLandscape && act != null && act.requestedOrientation != target) {
                    act.requestedOrientation = target
                }
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
