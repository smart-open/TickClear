package com.tickclear.app.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NoiseAssessment] 的国标分档与等能量换算校验。 */
class NoiseAssessmentTest {

    @Test
    fun `等效声级按能量平均而非算术平均`() {
        // 40 dB 与 100 dB 各占一半：算术平均为 70 dB，能量平均应接近 97 dB
        val leq = NoiseAssessment.equivalentLevel(listOf(40.0, 100.0))
        assertTrue("Leq=$leq 应显著高于算术平均 70", leq > 95.0)
        assertTrue("Leq=$leq 不应超过最大瞬时值 100", leq <= 100.0)
    }

    @Test
    fun `等声级序列的Leq等于该声级本身`() {
        val leq = NoiseAssessment.equivalentLevel(listOf(65.0, 65.0, 65.0, 65.0))
        assertEquals(65.0, leq, 0.0001)
    }

    @Test
    fun `空样本返回零`() {
        assertEquals(0.0, NoiseAssessment.equivalentLevel(emptyList()), 0.0)
    }

    @Test
    fun `评价等级边界符合国标分档`() {
        assertEquals(NoiseAssessment.Grade.EXCELLENT, NoiseAssessment.gradeOf(40.0))
        assertEquals(NoiseAssessment.Grade.GOOD, NoiseAssessment.gradeOf(40.1))
        assertEquals(NoiseAssessment.Grade.GOOD, NoiseAssessment.gradeOf(55.0))
        assertEquals(NoiseAssessment.Grade.FAIR, NoiseAssessment.gradeOf(55.1))
        assertEquals(NoiseAssessment.Grade.FAIR, NoiseAssessment.gradeOf(70.0))
        assertEquals(NoiseAssessment.Grade.POOR, NoiseAssessment.gradeOf(70.1))
        assertEquals(NoiseAssessment.Grade.POOR, NoiseAssessment.gradeOf(85.0))
        assertEquals(NoiseAssessment.Grade.HARMFUL, NoiseAssessment.gradeOf(85.1))
        assertEquals(NoiseAssessment.Grade.HARMFUL, NoiseAssessment.gradeOf(100.0))
        assertEquals(NoiseAssessment.Grade.DANGEROUS, NoiseAssessment.gradeOf(100.1))
    }

    @Test
    fun `职业接触时长遵循85分贝8小时每增3分贝减半`() {
        assertNull("低于 80 dB(A) 不限制接触时长", NoiseAssessment.allowedExposureHours(79.9))
        assertEquals(8.0, NoiseAssessment.allowedExposureHours(85.0)!!, 0.0001)
        assertEquals(4.0, NoiseAssessment.allowedExposureHours(88.0)!!, 0.0001)
        assertEquals(2.0, NoiseAssessment.allowedExposureHours(91.0)!!, 0.0001)
        assertEquals(16.0, NoiseAssessment.allowedExposureHours(82.0)!!, 0.0001)
        assertEquals(0.0, NoiseAssessment.allowedExposureHours(115.1)!!, 0.0)
    }

    @Test
    fun `功能区昼间限值对照正确`() {
        assertEquals(NoiseAssessment.FunctionZone.ZONE_0, NoiseAssessment.strictestDayZone(50.0))
        assertEquals(NoiseAssessment.FunctionZone.ZONE_1, NoiseAssessment.strictestDayZone(55.0))
        assertEquals(NoiseAssessment.FunctionZone.ZONE_2, NoiseAssessment.strictestDayZone(56.0))
        assertEquals(NoiseAssessment.FunctionZone.ZONE_4A, NoiseAssessment.strictestDayZone(70.0))
        assertNull("超过 70 dB(A) 昼间无达标功能区", NoiseAssessment.strictestDayZone(70.1))
    }

    @Test
    fun `功能区夜间限值对照正确`() {
        assertEquals(NoiseAssessment.FunctionZone.ZONE_0, NoiseAssessment.strictestNightZone(40.0))
        assertEquals(NoiseAssessment.FunctionZone.ZONE_1, NoiseAssessment.strictestNightZone(45.0))
        assertEquals(NoiseAssessment.FunctionZone.ZONE_3, NoiseAssessment.strictestNightZone(55.0))
        assertNull("超过 55 dB(A) 夜间无达标功能区", NoiseAssessment.strictestNightZone(55.1))
    }
}
