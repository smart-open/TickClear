package com.tickclear.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── 品牌色（D.7 设计令牌）──────────────────────────────
val ClearBlue = Color(0xFF2F6BFF)      // 清空蓝 · 主色
val ClearBlueDark = Color(0xFF6E9BFF)
val Mint = Color(0xFF21C19B)           // 薄荷绿（扩展）
val Violet = Color(0xFF7C5CFF)
val Ink = Color(0xFF1A1C20)
val InkSoft = Color(0xFF5A6068)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF121419)
val Danger = Color(0xFFE5484D)
val Warning = Color(0xFFF5A623)
val Success = Color(0xFF2BA24A)

internal val LightColorScheme = lightColorScheme(
    primary = ClearBlue,
    onPrimary = Color.White,
    secondary = ClearBlueDark,
    onSecondary = Color.White,
    tertiary = Violet,
    background = SurfaceLight,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = InkSoft,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFFD5DAE2),
    primaryContainer = Color(0xFFE4ECFF),
    onPrimaryContainer = Color(0xFF0B3AA8),
)

internal val DarkColorScheme = darkColorScheme(
    primary = ClearBlueDark,
    onPrimary = Ink,
    secondary = ClearBlue,
    onSecondary = Color.White,
    tertiary = Color(0xFF9B86FF),
    background = SurfaceDark,
    onBackground = Color(0xFFE8EAEF),
    surface = Color(0xFF1B1F27),
    onSurface = Color(0xFFE8EAEF),
    surfaceVariant = Color(0xFF262B35),
    onSurfaceVariant = Color(0xFFAEB6C2),
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF3A4150),
    primaryContainer = Color(0xFF1B3A8C),
    onPrimaryContainer = Color(0xFFCFDBFF),
)

// ── V2.68 主题皮肤（预设配色）─────────────────────────
// 皮肤独立于「浅色/深色/动态」模式：最终配色 = 模式(明/暗/动态) × 皮肤(主色种子)。
// BLUE 复用既有 LightColorScheme/DarkColorScheme，保证零视觉回归；其余皮肤由种子色经 HSL 派生。

enum class ThemeSkin {
    BLUE, GREEN, PURPLE, ORANGE, ROSE, TEAL
}

private data class SkinSeeds(val primary: Color, val secondary: Color, val tertiary: Color)

private val SKIN_SEEDS: Map<ThemeSkin, SkinSeeds> = mapOf(
    ThemeSkin.BLUE to SkinSeeds(ClearBlue, ClearBlueDark, Violet),
    ThemeSkin.GREEN to SkinSeeds(Color(0xFF21C19B), Color(0xFF4CAF50), Color(0xFF009688)),
    ThemeSkin.PURPLE to SkinSeeds(Color(0xFF7C5CFF), Color(0xFF9C27B0), Color(0xFF673AB7)),
    ThemeSkin.ORANGE to SkinSeeds(Color(0xFFE8730C), Color(0xFFFF9800), Color(0xFFFF5722)),
    ThemeSkin.ROSE to SkinSeeds(Color(0xFFE91E63), Color(0xFFAD1457), Color(0xFFD81B60)),
    ThemeSkin.TEAL to SkinSeeds(Color(0xFF00897B), Color(0xFF26A69A), Color(0xFF1DE9B6)),
)

// ── HSL 颜色工具（零依赖，用于从种子色派生容器/前景色）──
private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    var h = 0f
    var s = 0f
    val d = max - min
    if (d != 0f) {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        when (max) {
            r -> h = (g - b) / d + (if (g < b) 6f else 0f)
            g -> h = (b - r) / d + 2f
            b -> h = (r - g) / d + 4f
        }
        h /= 6f
    }
    return floatArrayOf(h, s, l)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val r: Float
    val g: Float
    val b: Float
    if (s == 0f) {
        r = l; g = l; b = l
    } else {
        fun hue2rgb(p: Float, q: Float, t: Float): Float {
            var tt = t
            if (tt < 0f) tt += 1f
            if (tt > 1f) tt -= 1f
            if (tt < 1f / 6f) return p + (q - p) * 6f * tt
            if (tt < 1f / 2f) return q
            if (tt < 2f / 3f) return p + (q - p) * (2f / 3f - tt) * 6f
            return p
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        r = hue2rgb(p, q, h + 1f / 3f)
        g = hue2rgb(p, q, h)
        b = hue2rgb(p, q, h - 1f / 3f)
    }
    return Color(red = r, green = g, blue = b)
}

/** 仅替换明度（H/S 不变），用于派生容器/前景色，结果可预测。 */
private fun Color.withLightness(l: Float): Color {
    val hsl = toHsl()
    return hslColor(hsl[0], hsl[1], l.coerceIn(0f, 1f))
}

/** 根据当前明度返回可读前景色：亮底用墨色，暗底用白色。 */
private fun Color.onFor(): Color = if (toHsl()[2] > 0.55f) Ink else Color.White

private fun buildSkinScheme(seeds: SkinSeeds, dark: Boolean): ColorScheme {
    return if (dark) {
        val p = seeds.primary.withLightness(0.62f)
        val s = seeds.secondary.withLightness(0.58f)
        val t = seeds.tertiary.withLightness(0.58f)
        darkColorScheme(
            primary = p,
            onPrimary = p.onFor(),
            secondary = s,
            onSecondary = s.onFor(),
            tertiary = t,
            onTertiary = t.onFor(),
            background = SurfaceDark,
            onBackground = Color(0xFFE8EAEF),
            surface = Color(0xFF1B1F27),
            onSurface = Color(0xFFE8EAEF),
            surfaceVariant = Color(0xFF262B35),
            onSurfaceVariant = Color(0xFFAEB6C2),
            error = Danger,
            onError = Color.White,
            outline = Color(0xFF3A4150),
            primaryContainer = seeds.primary.withLightness(0.22f),
            onPrimaryContainer = seeds.primary.withLightness(0.82f),
        )
    } else {
        val p = seeds.primary
        val s = seeds.secondary
        val t = seeds.tertiary
        lightColorScheme(
            primary = p,
            onPrimary = p.onFor(),
            secondary = s,
            onSecondary = s.onFor(),
            tertiary = t,
            onTertiary = t.onFor(),
            background = SurfaceLight,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEEF1F6),
            onSurfaceVariant = InkSoft,
            error = Danger,
            onError = Color.White,
            outline = Color(0xFFD5DAE2),
            primaryContainer = seeds.primary.withLightness(0.90f),
            onPrimaryContainer = seeds.primary.withLightness(0.30f),
        )
    }
}

/** 解析某皮肤在指定明暗下的 ColorScheme；BLUE 直接复用既有方案以保证零回归。 */
fun skinScheme(skin: ThemeSkin, dark: Boolean): ColorScheme {
    if (skin == ThemeSkin.BLUE) return if (dark) DarkColorScheme else LightColorScheme
    val seeds = SKIN_SEEDS[skin] ?: SKIN_SEEDS.getValue(ThemeSkin.BLUE)
    return buildSkinScheme(seeds, dark)
}

/** 设置页皮肤选择器用的预览主色。 */
fun skinPreviewColor(skin: ThemeSkin): Color =
    (SKIN_SEEDS[skin] ?: SKIN_SEEDS.getValue(ThemeSkin.BLUE)).primary
