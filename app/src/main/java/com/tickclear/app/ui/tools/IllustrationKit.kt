package com.tickclear.app.ui.tools

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 插画工具库（V2.9++ 美化专项）。
 *
 * 目标：把"平涂圆/椭圆"升级为具备体积感、柔和投影与边缘光的拟物绘制，
 * 让模拟解压/动物/物件类工具摆脱"丑陋"观感，且保持零新依赖、纯 Canvas。
 *
 * 设计取舍：
 * - 全部为 [DrawScope] 顶层扩展，覆盖坐标空间与调用方一致，不引入额外状态。
 * - 明暗用径向渐变（高光→基色→暗部）模拟球面/椭球受光，比单色 flat 立体得多。
 * - 接触投影用径向渐变（黑→透明）伪造软阴影，不依赖 blur 后处理。
 * - 颜色派生（lighten/darken）本地自算，避免 `lerp` 等导入歧义。
 */

/** 向白色靠拢，t∈[0,1]，用于高光。 */
fun Color.lighten(t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * f,
        green = green + (1f - green) * f,
        blue = blue + (1f - blue) * f,
        alpha = alpha,
    )
}

/** 向黑色靠拢，t∈[0,1]，用于暗部/核心阴影。 */
fun Color.darken(t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - f),
        green = green * (1f - f),
        blue = blue * (1f - f),
        alpha = alpha,
    )
}

/** 柔和接触投影：在 [center] 处画一团黑→透明的径向渐变，模拟物体落地的软阴影。 */
fun DrawScope.drawContactShadow(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    maxAlpha: Float = 0.26f,
) {
    if (radiusX <= 0f || radiusY <= 0f) return
    val brush = Brush.radialGradient(
        colors = listOf(Color.Black.copy(alpha = maxAlpha), Color.Black.copy(alpha = 0f)),
        center = center,
        radius = max(radiusX, radiusY),
    )
    drawOval(
        brush = brush,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
    )
}

/**
 * 受光球面：径向渐变（高光→基色→暗部）由 [lightAngleDeg] 方向打光，
 * 并在背光侧描一圈低透明度边缘光，制造立体轮廓。
 */
fun DrawScope.fillSphere(
    center: Offset,
    radius: Float,
    base: Color,
    lightAngleDeg: Float = -50f,
    rimLight: Boolean = true,
) {
    if (radius <= 0f) return
    val rad = Math.toRadians(lightAngleDeg.toDouble())
    val lx = center.x + cos(rad).toFloat() * radius * 0.42f
    val ly = center.y + sin(rad).toFloat() * radius * 0.42f
    val brush = Brush.radialGradient(
        colors = listOf(base.lighten(0.55f), base, base.darken(0.45f)),
        center = Offset(lx, ly),
        radius = radius * 1.25f,
    )
    drawCircle(brush = brush, radius = radius, center = center)
    if (rimLight) {
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = radius * 0.97f,
            center = center,
            style = Stroke(width = max(1f, radius * 0.05f)),
        )
    }
}

/** 受光椭球（椭圆），与 [fillSphere] 同理，渐变中心偏 [lightAngleDeg] 方向。 */
fun DrawScope.fillOvoid(
    topLeft: Offset,
    size: Size,
    base: Color,
    lightAngleDeg: Float = -50f,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val cx = topLeft.x + size.width / 2f
    val cy = topLeft.y + size.height / 2f
    val rad = Math.toRadians(lightAngleDeg.toDouble())
    val hx = cx + cos(rad).toFloat() * size.width * 0.30f
    val hy = cy + sin(rad).toFloat() * size.height * 0.30f
    val brush = Brush.radialGradient(
        colors = listOf(base.lighten(0.5f), base, base.darken(0.42f)),
        center = Offset(hx, hy),
        radius = max(size.width, size.height) * 0.95f,
    )
    drawOval(brush = brush, topLeft = topLeft, size = size)
}

/** 受光圆角矩形（木鱼/杯身等物件），渐变中心偏 [lightAngleDeg] 方向。 */
fun DrawScope.fillRoundRect3D(
    topLeft: Offset,
    size: Size,
    cornerRadius: Float,
    base: Color,
    lightAngleDeg: Float = -50f,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val cx = topLeft.x + size.width / 2f
    val cy = topLeft.y + size.height / 2f
    val rad = Math.toRadians(lightAngleDeg.toDouble())
    val hx = cx + cos(rad).toFloat() * size.width * 0.32f
    val hy = cy + sin(rad).toFloat() * size.height * 0.32f
    val brush = Brush.radialGradient(
        colors = listOf(base.lighten(0.42f), base, base.darken(0.4f)),
        center = Offset(hx, hy),
        radius = max(size.width, size.height) * 0.95f,
    )
    drawRoundRect(
        brush = brush,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
    )
}

/** 受光闭合路径（耳朵/尾巴/鳍等不规则形状），渐变中心取路径包围盒顶部偏光。 */
fun DrawScope.fillPath3D(
    path: Path,
    base: Color,
    lightAngleDeg: Float = -50f,
) {
    val b = path.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val cx = b.center.x
    val cy = b.center.y
    val rad = Math.toRadians(lightAngleDeg.toDouble())
    val hx = cx + cos(rad).toFloat() * b.width * 0.3f
    val hy = cy + sin(rad).toFloat() * b.height * 0.3f
    val brush = Brush.radialGradient(
        colors = listOf(base.lighten(0.4f), base, base.darken(0.38f)),
        center = Offset(hx, hy),
        radius = max(b.width, b.height) * 0.95f,
    )
    drawPath(path = path, brush = brush)
}

/** 局部光泽高光：在物体表面叠加一团小椭圆白光，强化"反光/湿润"质感。 */
fun DrawScope.drawGloss(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    alpha: Float = 0.5f,
) {
    if (radiusX <= 0f || radiusY <= 0f) return
    val brush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)),
        center = center,
        radius = max(radiusX, radiusY),
    )
    drawOval(
        brush = brush,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
    )
}

/**
 * 是否应减少动态效果（无障碍）。
 * 读系统 `ANIMATOR_DURATION_SCALE`：用户开启"移除动画"时返回 true，
 * 调用方据此冻结 idle 动画、只保留必要反馈（遵循 reduced-motion 原则）。
 */
fun isMotionReduced(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (_: Exception) {
    false
}
