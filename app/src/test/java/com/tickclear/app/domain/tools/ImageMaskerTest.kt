package com.tickclear.app.domain.tools

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImageMasker.MaskShape] 数据校验的纯逻辑测试。
 * 涉及 [android.graphics.Bitmap] 的端到端渲染在 instrumented test 中验证。
 */
class ImageMaskerTest {

    @Test
    fun `MaskShape Stroke widthRatio 为 0 抛 IllegalArgumentException`() {
        val ok = runCatching {
            ImageMasker.MaskShape.Stroke(listOf(0f to 0f, 1f to 1f), 0f)
        }.isFailure
        assertTrue("widthRatio=0 必须抛 IllegalArgumentException", ok)
    }

    @Test
    fun `MaskShape Stroke widthRatio 为负 抛 IllegalArgumentException`() {
        val ok = runCatching {
            ImageMasker.MaskShape.Stroke(listOf(0f to 0f, 1f to 1f), -0.05f)
        }.isFailure
        assertTrue("widthRatio=-0.05 必须抛 IllegalArgumentException", ok)
    }

    @Test
    fun `MaskShape Stroke widthRatio 正常 构造成功`() {
        val ok = runCatching {
            ImageMasker.MaskShape.Stroke(emptyList(), 0.04f)
        }.isSuccess
        assertTrue("空点列表 + widthRatio=0.04 应允许构造（渲染时数量检查跳过即可）", ok)
    }

    @Test
    fun `MaskShape Box 任意 0 到 1 区间 构造成功`() {
        val ok = runCatching {
            ImageMasker.MaskShape.Box(0.1f, 0.2f, 0.3f, 0.4f)
        }.isSuccess
        assertTrue("Box 应允许任意 0 到 1 区间", ok)
    }
}
