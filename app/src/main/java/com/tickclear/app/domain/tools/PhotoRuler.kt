package com.tickclear.app.domain.tools

/**
 * 拍照测距：让用户先在同一平面内放置一个已知尺寸的参照物（如硬币、银行卡、身份证、A4 纸），
 * 分别在屏幕上点取参照物的两端与目标物的两端，按像素比例换算得到目标的实际长度。
 *
 * 核心假设是「参照物与目标物共面、且均与镜头光轴平行」，
 * 实际拍摄时偏差越大、误差越大，UI 上需要明确告知用户。
 */
object PhotoRuler {

    /** 预设参照物（均为长边尺寸，单位 mm）。 */
    data class ReferencePreset(
        val key: String,
        val label: String,
        val mm: Float,
    )

    val PRESETS: List<ReferencePreset> = listOf(
        ReferencePreset("coin_1rmb", "1 元硬币（直径 25.0 mm）", 25.0f),
        ReferencePreset("coin_50fen", "5 角硬币（直径 20.5 mm）", 20.5f),
        ReferencePreset("id_card", "身份证（长边 85.6 mm）", 85.6f),
        ReferencePreset("bank_card", "银行卡（长边 85.6 mm）", 85.6f),
        ReferencePreset("a4_short", "A4 纸短边（210 mm）", 210.0f),
        ReferencePreset("a4_long", "A4 纸长边（297 mm）", 297.0f),
    )

    /**
     * 由参照物像素长度与目标像素长度算出目标实际长度（mm）。
     *
     * @param referenceMm 参照物的实际长度（mm），必须 > 0。
     * @param referencePx 参照物两端在图像上的像素距离，必须 > 0。
     * @param targetPx 目标物两端在图像上的像素距离。
     * @return 目标物实际长度（mm）；若任一参数不合法则返回 `null`。
     */
    fun measure(
        referenceMm: Float,
        referencePx: Float,
        targetPx: Float,
    ): Float? {
        if (referenceMm <= 0f || referencePx <= 0f) return null
        return targetPx.coerceAtLeast(0f) * referenceMm / referencePx
    }

    /** 两点像素距离。 */
    fun pixelDistance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
