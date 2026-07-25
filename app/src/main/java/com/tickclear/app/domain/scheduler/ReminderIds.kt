package com.tickclear.app.domain.scheduler

/**
 * 提醒通知 / 意图 id 稳定映射（V2.52）。
 *
 * 原先通知 id 与各 PendingIntent requestCode 散落多处直接调用 `String.hashCode()`：
 * 不同用途（通知本身、打开应用、完成、稍后、跳过、全屏）仅靠「拼接后缀再 hashCode」
 * 区分，且调度端（[ReminderScheduler]）与接收端（[ReminderReceiver]）各自独立取哈希，
 * 一旦映射口径漂移就会互相覆盖。
 *
 * 这里统一收敛到单一确定性映射：
 * 1. 采用 FNV-1a 32 位算法（比默认 `String.hashCode` 在短串上分布更离散）；
 * 2. 每个用途使用独立前缀，保证「同一 instanceId 的不同动作恒得到不同 id」；
 * 3. 结果清除符号位（非负 int），避免通知 id 取负值带来的边界风险（如 id=0 特殊处理）。
 *
 * 调度端与接收端共用本对象，确保两端映射完全一致。
 */
object ReminderIds {

    // FNV-1a 32 位偏移基（0x811c9dc5 的有符号 Int 表示）。
    private const val FNV_OFFSET_BASIS = -0x7ee3623b
    // FNV prime = 16777619，小于 Int.MAX_VALUE，可用正数 Int 表示。
    private const val FNV_PRIME = 0x01000193

    /** FNV-1a 32 位哈希，返回非负 int，作为稳定 id。 */
    fun fnv1a(input: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (b in input.encodeToByteArray()) {
            hash = hash xor (b.toInt() and 0xFF)
            hash = multiplyPrime(hash)
        }
        return hash and Int.MAX_VALUE
    }

    // 32 位无符号乘法后折叠回 Int，避免有符号溢出截断带来的分布偏差。
    private fun multiplyPrime(hash: Int): Int =
        (hash.toUInt() * FNV_PRIME.toUInt()).toInt()

    /** 通知 id（亦用于 cancel）。调度端与接收端必须一致。 */
    fun notificationId(instanceId: String): Int = fnv1a("n:$instanceId")

    /** 通知「打开应用」内容意图 requestCode。 */
    fun contentRequestCode(instanceId: String): Int = fnv1a("c:$instanceId")

    /** 通知「完成」动作意图 requestCode。 */
    fun completeRequestCode(instanceId: String): Int = fnv1a("complete:$instanceId")

    /** 通知「稍后提醒」动作意图 requestCode。 */
    fun snoozeRequestCode(instanceId: String): Int = fnv1a("snooze:$instanceId")

    /** 通知「跳过」动作意图 requestCode。 */
    fun skipRequestCode(instanceId: String): Int = fnv1a("skip:$instanceId")

    /** 高优先级全屏意图 requestCode（基于 taskId，与实例无关）。 */
    fun fullScreenRequestCode(taskId: String): Int = fnv1a("fs:$taskId")
}
