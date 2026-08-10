package com.tickclear.app.ui.tools

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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

/** 全局统一光源方向（度）：所有受光绘制默认从此角度打光，保证整套工具光影一致。 */
const val LIGHT_ANGLE_DEG: Float = -50f

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
 * 柔和接地阴影（二巡升级）：多层径向渐变叠加，模拟真实软阴影的半影（penumbra），
 * 比 [drawContactShadow] 单层更自然、更有"落地感"。物体投影默认用它。
 */
fun DrawScope.drawSoftShadow(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    maxAlpha: Float = 0.30f,
) {
    if (radiusX <= 0f || radiusY <= 0f) return
    // 外层：大而淡，模拟半影扩散
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = maxAlpha * 0.45f), Color.Black.copy(alpha = 0f)),
            center = center,
            radius = max(radiusX, radiusY) * 1.45f,
        ),
        topLeft = Offset(center.x - radiusX * 1.45f, center.y - radiusY * 1.45f),
        size = Size(radiusX * 2.9f, radiusY * 2.9f),
    )
    // 内层：小而深，模拟接触核心暗部
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = maxAlpha), Color.Black.copy(alpha = 0f)),
            center = center,
            radius = max(radiusX, radiusY),
        ),
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
    lightAngleDeg: Float = LIGHT_ANGLE_DEG,
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
    lightAngleDeg: Float = LIGHT_ANGLE_DEG,
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
    lightAngleDeg: Float = LIGHT_ANGLE_DEG,
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
    lightAngleDeg: Float = LIGHT_ANGLE_DEG,
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
 * 边缘辉光（二巡升级）：沿圆形轮廓描一圈基于材质色的低透明度光晕，
 * 比纯白描边更有"材质感"，让球面/物件边缘透出柔和辉光，提升精致度。
 * 典型用法：把 [fillSphere] 的纯白 rim 关掉（rimLight=false），改用本函数以基色派生色描边。
 */
fun DrawScope.drawRimLight(
    center: Offset,
    radius: Float,
    tint: Color,
    alpha: Float = 0.35f,
    width: Float? = null,
) {
    if (radius <= 0f) return
    val w = width ?: max(1.5f, radius * 0.06f)
    drawCircle(
        color = tint.copy(alpha = alpha.coerceIn(0f, 1f)),
        radius = radius * 0.98f,
        center = center,
        style = Stroke(width = w),
    )
}

/**
 * 圆柱受光（横向渐变：暗边→高光带→基色→暗侧），适合蜡体/打火机机身等柱状物。
 * 比 [fillRoundRect3D] 的径向渐变更贴合"圆柱"的受光规律。
 */
fun DrawScope.fillCylinder(
    topLeft: Offset,
    size: Size,
    base: Color,
    cornerRadius: Float = 0f,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val brush = Brush.horizontalGradient(
        0f to base.darken(0.34f),
        0.16f to base.lighten(0.40f),
        0.34f to base,
        0.62f to base.darken(0.20f),
        0.88f to base.darken(0.40f),
        1f to base.darken(0.16f),
        startX = topLeft.x,
        endX = topLeft.x + size.width,
    )
    drawRoundRect(
        brush = brush,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
    )
}

/** 火焰水滴形轮廓：底部两侧收拢、顶端拉尖，[leanX] 让尖端随风偏移。 */
private fun flamePath(baseX: Float, baseY: Float, halfWidth: Float, height: Float, leanX: Float): Path {
    val tipX = baseX + leanX
    return Path().apply {
        moveTo(baseX, baseY)
        cubicTo(
            baseX - halfWidth, baseY - height * 0.32f,
            tipX - halfWidth * 0.62f, baseY - height * 0.74f,
            tipX, baseY - height,
        )
        cubicTo(
            tipX + halfWidth * 0.62f, baseY - height * 0.74f,
            baseX + halfWidth, baseY - height * 0.32f,
            baseX, baseY,
        )
        close()
    }
}

/**
 * 拟真火焰：暖色光晕 + 外焰（深橙→橙渐变）+ 内焰（黄）+ 焰心白热点 + 根部蓝焰。
 * [baseX]/[baseY] 为焰底（烛芯/出气口）坐标，[leanX] 为风吹偏移，[alpha] 控整体淡出。
 */
fun DrawScope.drawFlame(
    baseX: Float,
    baseY: Float,
    halfWidth: Float,
    height: Float,
    leanX: Float = 0f,
    alpha: Float = 1f,
) {
    if (halfWidth <= 0f || height <= 0f) return
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.01f) return

    // 光晕：让火焰"照亮"周围物体
    val haloCenter = Offset(baseX + leanX * 0.4f, baseY - height * 0.45f)
    val haloR = height * 1.5f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFCC80).copy(alpha = 0.34f * a),
                Color(0xFFFF9800).copy(alpha = 0.12f * a),
                Color(0xFFFF9800).copy(alpha = 0f),
            ),
            center = haloCenter,
            radius = haloR,
        ),
        radius = haloR,
        center = haloCenter,
    )
    // 外焰
    drawPath(
        path = flamePath(baseX, baseY, halfWidth, height, leanX),
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = a),
                Color(0xFFFF7043).copy(alpha = a),
                Color(0xFFE64A19).copy(alpha = a * 0.9f),
            ),
            center = Offset(baseX, baseY - height * 0.26f),
            radius = height * 0.92f,
        ),
    )
    // 内焰
    drawPath(
        path = flamePath(baseX, baseY - height * 0.02f, halfWidth * 0.56f, height * 0.64f, leanX * 0.7f),
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFDE7).copy(alpha = a),
                Color(0xFFFFEB3B).copy(alpha = a),
                Color(0xFFFFA726).copy(alpha = a * 0.6f),
            ),
            center = Offset(baseX, baseY - height * 0.20f),
            radius = height * 0.6f,
        ),
    )
    // 焰心白热点
    drawGloss(
        center = Offset(baseX + leanX * 0.2f, baseY - height * 0.26f),
        radiusX = halfWidth * 0.34f,
        radiusY = height * 0.20f,
        alpha = 0.72f * a,
    )
    // 根部蓝焰（真实蜡烛/打火机的低温区）
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF64B5F6).copy(alpha = 0.55f * a),
                Color(0xFF2196F3).copy(alpha = 0f),
            ),
            center = Offset(baseX, baseY - height * 0.05f),
            radius = halfWidth * 1.1f,
        ),
        topLeft = Offset(baseX - halfWidth * 0.9f, baseY - height * 0.20f),
        size = Size(halfWidth * 1.8f, height * 0.26f),
    )
}

// ---------------------------------------------------------------------------
// 等距立方体（圆角 + 渐变受光）：由抽签器 3D 骰子沉淀而来的通用管线
// ---------------------------------------------------------------------------

/** 三维向量，同时用作立方体局部坐标（半边长为 1，y 轴向上）。 */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** 绕 X 轴旋转 [radians] 弧度。 */
fun Vec3.rotX(radians: Float): Vec3 {
    val c = cos(radians)
    val s = sin(radians)
    return Vec3(x, y * c - z * s, y * s + z * c)
}

/** 绕 Y 轴旋转 [radians] 弧度。 */
fun Vec3.rotY(radians: Float): Vec3 {
    val c = cos(radians)
    val s = sin(radians)
    return Vec3(x * c + z * s, y, -x * s + z * c)
}

/** 等距投影系数：cos30° / sin30°。 */
private const val ISO_COS30 = 0.8660254f
private const val ISO_SIN30 = 0.5f

/**
 * 等距投影：把立方体局部坐标 [p] 投到屏幕。
 * [center] 为立方体中心的屏幕坐标，[scale] 为"局部 1 个单位"对应的像素数（即半边长）。
 * 屏幕 y 轴向下，故局部 y 取负。
 */
fun projectIso(p: Vec3, center: Offset, scale: Float): Offset {
    val sx = (p.x - p.z) * ISO_COS30
    val sy = (p.x + p.z) * ISO_SIN30 - p.y
    return Offset(center.x + sx * scale, center.y + sy * scale)
}

/** 立方体 6 个面。[normal] 为局部外法线。 */
enum class CubeFace(val normal: Vec3) {
    Top(Vec3(0f, 1f, 0f)),
    Bottom(Vec3(0f, -1f, 0f)),
    Front(Vec3(0f, 0f, 1f)),
    Back(Vec3(0f, 0f, -1f)),
    Right(Vec3(1f, 0f, 0f)),
    Left(Vec3(-1f, 0f, 0f)),
    ;

    /** 把面内坐标 (u,v)∈[-1,1] 映射到立方体局部 3D 坐标，(0,0) 为面心。 */
    fun point(u: Float, v: Float): Vec3 = when (this) {
        Top -> Vec3(u, 1f, v)
        Bottom -> Vec3(u, -1f, v)
        Front -> Vec3(u, v, 1f)
        Back -> Vec3(u, v, -1f)
        Right -> Vec3(1f, v, u)
        Left -> Vec3(-1f, v, u)
    }
}

/**
 * 圆角多边形路径：用 quadratic 贝塞尔对每个顶点做切角，
 * 让棱角圆润光滑而非硬直线。切角半径自动受相邻边长一半的钳制，短边不会塌陷。
 */
fun roundedPolyPath(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    if (n < 3) return path
    for (i in 0 until n) {
        val a = points[(i - 1 + n) % n]
        val b = points[i]
        val c = points[(i + 1) % n]
        val l1 = (b - a).getDistance()
        val l2 = (c - b).getDistance()
        if (l1 <= 0f || l2 <= 0f) continue
        val r = min(radius, min(l1 / 2f, l2 / 2f))
        val p1 = b + (a - b) * (r / l1)
        val p2 = b + (c - b) * (r / l2)
        if (path.isEmpty) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
        path.quadraticTo(b.x, b.y, p2.x, p2.y)
    }
    if (!path.isEmpty) path.close()
    return path
}

/** 单面绘制数据：深度用于排序、投影四角用于填充/描边、圆角半径与面心用于受光渐变。 */
private class CubeFaceDraw(
    val depth: Float,
    val face: CubeFace,
    val proj: List<Offset>,
    val cornerR: Float,
    val faceCenter: Offset,
)

/**
 * 通用 3D 立方体：等距投影 + 背面剔除 + 深度排序 + 圆角切角 + 每面线性渐变受光。
 *
 * 与"平面圆角矩形 + graphicsLayer 翻转"的假 3D 不同，这里是真的把 8 个顶点投影出来，
 * 只画外法线朝向观察者（·(1,1,1)>0）的面，并按深度从远到近覆盖绘制（画家算法）。
 *
 * @param center 立方体中心的屏幕坐标
 * @param scale 半边长（像素）。整体外接尺寸约为 `scale * 3.46`（宽）× `scale * 3`（高）
 * @param rotX/[rotY] 绕 X/Y 轴的旋转弧度；均为 2π 整数倍时回到零位（正面朝向观察者）
 * @param faceColor 各面基色，渐变会在其上派生高光/暗部
 * @param edgeColor 实心细描边颜色
 * @param softEdgeColor 更宽更淡的柔边颜色，先于细边绘制，让棱角过渡更顺滑
 * @param cornerRadiusRatio 圆角半径 / 该面最短边长，默认 0.34
 * @param maxCornerRatio 圆角半径上限 / [scale]，避免大面过度圆化
 * @param lightDir 屏幕空间光源方向（单位向量，默认左上）
 * @param shadow 是否绘制接地软阴影
 * @param faceContent 可选的面内容绘制回调：入参为面本身与"面内坐标 (u,v)∈[-1,1] → 屏幕坐标"的映射函数，
 *   内容随立方体一起旋转（例如骰子点数、图标、文字底纹）
 */
fun DrawScope.drawCube3D(
    center: Offset,
    scale: Float,
    rotX: Float = 0f,
    rotY: Float = 0f,
    faceColor: (CubeFace) -> Color,
    edgeColor: Color = Color.Black.copy(alpha = 0.22f),
    softEdgeColor: Color = edgeColor.copy(alpha = edgeColor.alpha * 0.45f),
    cornerRadiusRatio: Float = 0.34f,
    maxCornerRatio: Float = 0.22f,
    lightDir: Offset = Offset(-0.55f, -0.83f),
    shadow: Boolean = true,
    faceContent: (DrawScope.(CubeFace, (Float, Float) -> Offset) -> Unit)? = null,
) {
    if (scale <= 0f) return

    if (shadow) {
        drawSoftShadow(
            center = Offset(center.x, center.y + scale * 1.45f),
            radiusX = scale * 1.5f,
            radiusY = scale * 0.5f,
            maxAlpha = 0.22f,
        )
    }

    // 预计算：背面剔除 + 投影四角 + 深度 + 自适应圆角半径 + 面心
    val visible = mutableListOf<CubeFaceDraw>()
    for (face in CubeFace.entries) {
        val rn = face.normal.rotX(rotX).rotY(rotY)
        if (rn.x + rn.y + rn.z <= 0.0001f) continue // 背向观察者
        val corners = listOf(
            face.point(-1f, -1f), face.point(1f, -1f),
            face.point(1f, 1f), face.point(-1f, 1f),
        ).map { it.rotX(rotX).rotY(rotY) }
        val proj = corners.map { projectIso(it, center, scale) }
        val depth = corners.sumOf { (it.x + it.y + it.z).toDouble() }.toFloat()
        val minEdge = (0..3).minOf { i -> (proj[i] - proj[(i + 1) % 4]).getDistance() }
        val cornerR = min(minEdge * cornerRadiusRatio, scale * maxCornerRatio)
        val fcx = proj.sumOf { it.x.toDouble() }.toFloat() / 4f
        val fcy = proj.sumOf { it.y.toDouble() }.toFloat() / 4f
        visible.add(CubeFaceDraw(depth, face, proj, cornerR, Offset(fcx, fcy)))
    }
    visible.sortBy { it.depth } // 远 → 近

    val edgeW = max(1f, scale * 0.02f)
    val spread = scale * 0.95f
    for (d in visible) {
        val base = faceColor(d.face)
        val brush = Brush.linearGradient(
            colors = listOf(base.lighten(0.22f), base.darken(0.20f)),
            start = Offset(d.faceCenter.x + lightDir.x * spread, d.faceCenter.y + lightDir.y * spread),
            end = Offset(d.faceCenter.x - lightDir.x * spread, d.faceCenter.y - lightDir.y * spread),
        )
        val path = roundedPolyPath(d.proj, d.cornerR)
        drawPath(path, brush)
        // 先描一层更宽更淡的柔边、再描细实心边，棱角更顺滑
        drawPath(path, softEdgeColor, style = Stroke(width = edgeW * 2.2f, join = StrokeJoin.Round))
        drawPath(path, edgeColor, style = Stroke(width = edgeW, join = StrokeJoin.Round))

        if (faceContent != null) {
            val mapper: (Float, Float) -> Offset = { u, v ->
                projectIso(d.face.point(u, v).rotX(rotX).rotY(rotY), center, scale)
            }
            faceContent(d.face, mapper)
        }
    }
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
