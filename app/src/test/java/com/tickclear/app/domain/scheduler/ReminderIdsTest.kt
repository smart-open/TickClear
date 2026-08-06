package com.tickclear.app.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2.52 验收：同一 instanceId 的不同动作（通知/打开/完成/稍后/跳过/全屏）必须得到
 * 互不相同且非负、确定性的稳定 id；不同 instanceId 通常也不应碰撞。
 */
class ReminderIdsTest {

    @Test
    fun sameInstanceIdDifferentActionsHaveDistinctIds() {
        val instanceId = "task-1@2026-07-25"
        val ids = setOf(
            ReminderIds.notificationId(instanceId),
            ReminderIds.contentRequestCode(instanceId),
            ReminderIds.completeRequestCode(instanceId),
            ReminderIds.snoozeRequestCode(instanceId),
            ReminderIds.skipRequestCode(instanceId),
            ReminderIds.fullScreenRequestCode("task-1"),
        )
        assertEquals("同一 instanceId 的 5 个动作 + taskId 全屏应全部互异", 6, ids.size)
    }

    @Test
    fun distinctInstanceIdsAreDistinct() {
        val a = "task-a@2026-07-25"
        val b = "task-b@2026-07-25"
        assertNotEquals(ReminderIds.notificationId(a), ReminderIds.notificationId(b))
    }

    @Test
    fun deterministicAcrossCalls() {
        val id = "x@today"
        assertEquals(ReminderIds.notificationId(id), ReminderIds.notificationId(id))
        assertEquals(ReminderIds.completeRequestCode(id), ReminderIds.completeRequestCode(id))
    }

    @Test
    fun idsAreNonNegative() {
        val id = "task-a@2026-07-25"
        // 注意：不可用 Kotlin 的 assert()，JVM 默认 -da 会整段跳过导致测试永远绿
        assertTrue("notificationId 必须非负", ReminderIds.notificationId(id) >= 0)
        assertTrue("contentRequestCode 必须非负", ReminderIds.contentRequestCode(id) >= 0)
        assertTrue("completeRequestCode 必须非负", ReminderIds.completeRequestCode(id) >= 0)
        assertTrue("snoozeRequestCode 必须非负", ReminderIds.snoozeRequestCode(id) >= 0)
        assertTrue("skipRequestCode 必须非负", ReminderIds.skipRequestCode(id) >= 0)
        assertTrue("fullScreenRequestCode 必须非负", ReminderIds.fullScreenRequestCode("task-a") >= 0)
    }
}
