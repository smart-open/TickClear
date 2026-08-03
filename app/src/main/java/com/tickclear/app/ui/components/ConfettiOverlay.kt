package com.tickclear.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 打卡撒花：纯 Canvas 粒子爆发动画（零新依赖）。
 * [trigger] 为递增的触发计数：每次自增即从中心向上方喷射一束彩纸，1.8s 内飞出屏幕并淡出。
 * [trigger] <= 0 时不渲染（初始态保持透明、不拦截点击）。
 * 动画结束后通过 [onFinished] 通知调用方重置 trigger，避免透明 Overlay 长期占住全屏组合。
 */
private val CONFETTI_COLORS = listOf(
    Color(0xFF2F6BFF), Color(0xFF21C19B), Color(0xFF7C5CFF),
    Color(0xFFF5A623), Color(0xFFE5484D), Color(0xFF3BA7F5),
)

private data class ConfettiParticle(
    val xN: Float,   // 起点（相对宽高，0..1）
    val yN: Float,
    val vxN: Float,  // 速度（相对宽度/高度，每单位 progress）
    val vyN: Float,
    val sizePx: Float,
    val color: Color,
    val rot: Float,
    val vr: Float,   // 旋转速度（度/单位 progress）
)

@Composable
fun ConfettiOverlay(
    trigger: Int,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null,
) {
    if (trigger <= 0) return

    val particles = remember(trigger) {
        val rnd = Random(trigger.toLong() * 2654435761L + 0x9E3779B9)
        List(90) {
            // 以正上方为中心向两侧扩散（±~46°），向上喷射。
            val angle = (-Math.PI / 2) + rnd.nextDouble(-0.8, 0.8)
            val speed = rnd.nextDouble(0.35, 0.85).toFloat()
            ConfettiParticle(
                xN = 0.5f,
                yN = 0.62f,
                vxN = cos(angle).toFloat() * speed,
                vyN = sin(angle).toFloat() * speed,
                sizePx = rnd.nextDouble(8.0, 16.0).toFloat(),
                color = CONFETTI_COLORS.random(rnd),
                rot = rnd.nextFloat() * 360f,
                vr = rnd.nextDouble(-420.0, 420.0).toFloat(),
            )
        }
    }

    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1800, easing = LinearEasing))
        onFinished?.invoke()
    }

    val p = progress.value
    // 最后 25% 时间整体淡出，避免粒子未落出屏幕就定格在画面上。
    val alpha = if (p > 0.75f) ((1f - p) / 0.25f).coerceIn(0f, 1f) else 1f
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 增强重力，让粒子在 1.8s 内尽快落出屏幕。
        val gravity = 1.4f * h
        for (pt in particles) {
            val x = pt.xN * w + pt.vxN * w * p
            val y = pt.yN * h + pt.vyN * h * p + 0.5f * gravity * p * p
            if (y > h + 40f || x < -40f || x > w + 40f || alpha <= 0f) continue
            rotate(pt.rot + pt.vr * p) {
                drawRect(
                    color = pt.color.copy(alpha = alpha),
                    topLeft = Offset(x - pt.sizePx, y - pt.sizePx),
                    size = Size(pt.sizePx * 2f, pt.sizePx * 2f),
                )
            }
        }
    }
}
