package com.tickclear.app.domain.tools

import kotlin.math.log10
import kotlin.math.pow

/**
 * 噪声测量结果评价（纯计算，无 Android 依赖，便于单元测试）。
 *
 * 依据：
 * - GB 3096-2008《声环境质量标准》——声环境功能区昼间/夜间环境噪声限值；
 * - GBZ 2.2-2007《工作场所有害因素职业接触限值 第2部分：物理因素》——
 *   噪声职业接触限值 8h 等效声级 85 dB(A)，声级每增加 3 dB 允许接触时间减半，
 *   且任何情况下不得接触超过 115 dB(A) 的噪声。
 */
object NoiseAssessment {

    /** GBZ 2.2-2007 每周 40h / 每日 8h 的噪声职业接触限值，单位 dB(A)。 */
    const val OCCUPATIONAL_LIMIT_DB: Double = 85.0

    /** GBZ 2.2-2007 规定任何情况下均不得接触的声级上限，单位 dB(A)。 */
    const val ABSOLUTE_LIMIT_DB: Double = 115.0

    /** 低于该声级时职业卫生标准不再限制每日接触时长，单位 dB(A)。 */
    const val NO_LIMIT_DB: Double = 80.0

    /** 评价等级（由低到高六档）。 */
    enum class Grade {
        /** ≤40 dB(A)，优。 */
        EXCELLENT,

        /** ≤55 dB(A)，良。 */
        GOOD,

        /** ≤70 dB(A)，一般。 */
        FAIR,

        /** ≤85 dB(A)，较差。 */
        POOR,

        /** ≤100 dB(A)，有害（达到/超过职业接触限值）。 */
        HARMFUL,

        /** >100 dB(A)，危险。 */
        DANGEROUS,
    }

    /**
     * GB 3096-2008 声环境功能区环境噪声等效声级限值，单位 dB(A)。
     *
     * 4b 类（铁路干线两侧）昼间同为 70 dB(A)，与 4a 类昼间限值一致，此处不单列。
     */
    enum class FunctionZone(val dayLimit: Int, val nightLimit: Int) {
        /** 0 类：康复疗养区等特别需要安静的区域。 */
        ZONE_0(50, 40),

        /** 1 类：居民住宅、医疗卫生、文化教育、行政办公为主的区域。 */
        ZONE_1(55, 45),

        /** 2 类：商业金融、集市贸易与居住混杂区域。 */
        ZONE_2(60, 50),

        /** 3 类：工业生产、仓储物流为主的区域。 */
        ZONE_3(65, 55),

        /** 4a 类：城市道路交通干线两侧区域。 */
        ZONE_4A(70, 55),
    }

    /** 把 A 计权声级换算为相对声能量，用于能量平均。 */
    fun toEnergy(db: Double): Double = 10.0.pow(db / 10.0)

    /** 由平均声能量还原等效连续 A 声级 Leq。 */
    fun fromEnergyMean(mean: Double): Double = if (mean <= 0.0) 0.0 else 10.0 * log10(mean)

    /**
     * 等效连续 A 声级 Leq = 10·lg( (1/n)·Σ 10^(Li/10) )。
     *
     * 注意必须按声能量平均而非算术平均，否则短促强噪声会被严重低估。
     */
    fun equivalentLevel(samples: List<Double>): Double {
        if (samples.isEmpty()) return 0.0
        return fromEnergyMean(samples.sumOf { toEnergy(it) } / samples.size)
    }

    /** 按等效声级给出评价等级。 */
    fun gradeOf(leqDb: Double): Grade = when {
        leqDb <= 40.0 -> Grade.EXCELLENT
        leqDb <= 55.0 -> Grade.GOOD
        leqDb <= 70.0 -> Grade.FAIR
        leqDb <= 85.0 -> Grade.POOR
        leqDb <= 100.0 -> Grade.HARMFUL
        else -> Grade.DANGEROUS
    }

    /**
     * GBZ 2.2-2007 等能量原则下的每日允许接触时间。
     *
     * @return 允许接触小时数；`null` 表示低于 80 dB(A)、标准不作时长限制；
     *         `0.0` 表示超过 115 dB(A)、任何时长均不允许接触。
     */
    fun allowedExposureHours(leqDb: Double): Double? = when {
        leqDb < NO_LIMIT_DB -> null
        leqDb > ABSOLUTE_LIMIT_DB -> 0.0
        else -> 8.0 * 2.0.pow((OCCUPATIONAL_LIMIT_DB - leqDb) / 3.0)
    }

    /**
     * 该声级在昼间可满足的最严格声环境功能区。
     *
     * @return 满足的最严格功能区；`null` 表示连要求最宽松的 4a 类昼间限值也已超过。
     */
    fun strictestDayZone(leqDb: Double): FunctionZone? =
        FunctionZone.entries.firstOrNull { leqDb <= it.dayLimit }

    /**
     * 该声级在夜间可满足的最严格声环境功能区。
     *
     * @return 满足的最严格功能区；`null` 表示所有类别夜间限值均已超过。
     */
    fun strictestNightZone(leqDb: Double): FunctionZone? =
        FunctionZone.entries.firstOrNull { leqDb <= it.nightLimit }
}
