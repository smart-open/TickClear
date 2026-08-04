package com.tickclear.app.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [PhotoRuler] 比例换算校验。 */
class PhotoRulerTest {

    @Test
    fun `比例换算与参照物等大时等于参照物尺寸`() {
        // 参照 100 mm 占 200 px，目标同样 200 px → 目标 100 mm
        val r = PhotoRuler.measure(referenceMm = 100f, referencePx = 200f, targetPx = 200f)
        assertEquals(100f, r!!, 0.0001f)
    }

    @Test
    fun `比例换算与参照物等比放大`() {
        // 参照 100 mm 占 200 px，目标 400 px → 200 mm（等比 2x）
        val r = PhotoRuler.measure(referenceMm = 100f, referencePx = 200f, targetPx = 400f)
        assertEquals(200f, r!!, 0.0001f)
    }

    @Test
    fun `参照像素为零返回 null`() {
        assertNull(PhotoRuler.measure(100f, 0f, 50f))
    }

    @Test
    fun `参照尺寸为零或负返回 null`() {
        assertNull(PhotoRuler.measure(0f, 200f, 50f))
        assertNull(PhotoRuler.measure(-1f, 200f, 50f))
    }

    @Test
    fun `目标像素为零返回零`() {
        val r = PhotoRuler.measure(100f, 200f, 0f)
        assertEquals(0f, r!!, 0.0001f)
    }

    @Test
    fun `像素距离用欧氏距离`() {
        assertEquals(5f, PhotoRuler.pixelDistance(0f, 0f, 3f, 4f), 0.0001f)
        assertEquals(0f, PhotoRuler.pixelDistance(1.5f, 2.5f, 1.5f, 2.5f), 0.0001f)
    }
}
