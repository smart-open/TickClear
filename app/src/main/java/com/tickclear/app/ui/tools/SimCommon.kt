package com.tickclear.app.ui.tools

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * 模拟解压屏共享的轻量粒子系统（V2.9++）。
 * 坐标全部归一化到画布尺寸（0..1，y 向下为正），绘制时乘 Size 还原为像素，
 * 这样粒子不受画布实际分辨率影响。纯 Canvas 绘制，零新依赖。
 */
data class SimParticle(
    var x: Float,       // 归一化坐标 0..1
    var y: Float,
    var vx: Float,      // 归一化速度（每秒）
    var vy: Float,
    var life: Float,    // 剩余存活秒
    var maxLife: Float,
    val hue: Float,     // 色相 0..360
    var radius: Float,  // 像素半径
    val ring: Boolean = false, // true=扩散圆环（敲木鱼涟漪）
)

/** 重力（归一化/秒²，y 向下为正）。 */
const val SIM_GRAVITY = 0.9f

/** 由色相生成鲜艳颜色，alpha 控制淡出。 */
fun simColor(hue: Float, alpha: Float): Color = Color.hsv(hue, 0.9f, 1f, alpha.coerceIn(0f, 1f))

/**
 * 推进一帧：原地修改对象属性（高效），返回存活粒子组成的新列表以触发 Canvas 重绘。
 * [dt] 为帧间隔（秒），调用方需夹紧上限避免卡顿后大跳。
 */
fun stepParticles(list: List<SimParticle>, dt: Float): List<SimParticle> {
    if (list.isEmpty()) return list
    val out = ArrayList<SimParticle>(list.size)
    for (p in list) {
        p.vy += SIM_GRAVITY * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.life -= dt
        if (p.ring) p.radius += 90f * dt // 圆环匀速扩张
        if (p.life > 0f && p.y < 1.2f && p.y > -0.2f && p.x > -0.2f && p.x < 1.2f) out.add(p)
    }
    return out
}

/** 标准帧循环推进：返回新的粒子列表（dt 已夹紧）。配合 withFrameMillis 使用。 */
fun tickParticles(
    particles: List<SimParticle>,
    nowMs: Long,
    lastMs: Long,
): Pair<List<SimParticle>, Long> {
    val dt = if (lastMs == 0L) 0.016f else ((nowMs - lastMs) / 1000f).coerceAtMost(0.05f)
    return stepParticles(particles, dt) to nowMs
}

/** 在 [center]（归一化）处生成一次径向爆发，[count] 个粒子。 */
fun burst(
    cx: Float,
    cy: Float,
    count: Int,
    speed: Float,
    life: Float,
    hues: List<Float>,
    radiusPx: Float,
): List<SimParticle> = List(count) { i ->
    val ang = (i.toFloat() / count) * 2 * PI_F - PI_F / 2f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.4f
    val sp = speed * (0.6f + kotlin.random.Random.nextFloat() * 0.6f)
    SimParticle(
        x = cx,
        y = cy,
        vx = kotlin.math.cos(ang) * sp,
        vy = kotlin.math.sin(ang) * sp,
        life = life * (0.7f + kotlin.random.Random.nextFloat() * 0.5f),
        maxLife = life,
        hue = hues[kotlin.random.Random.nextInt(hues.size)],
        radius = radiusPx * (0.6f + kotlin.random.Random.nextFloat() * 0.8f),
    )
}

private const val PI_F = 3.1415927f

/** 计算两点归一化距离。 */
fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
