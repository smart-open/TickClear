package com.tickclear.app.domain.backup

import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.TaskRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 备份健康校验测试（V2.22 / V2.23）：验证 [BackupManager.validateBackupJson] 对
 * 合法 / 损坏 / 版本非法 / 版本过高 / 空备份 的判定，规则对齐 [BackupManager.importFromJson]。
 * 纯 JVM，不依赖 Android 上下文。
 */
class BackupManagerHealthTest {

    private fun bm() = BackupManager(
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true),
    )

    @Test
    fun `合法备份 JSON 校验为 OK`() {
        val json = """{"app":"TickClear","schemaVersion":1,"exportedAt":0,"groups":[],"tasks":[{"id":"t1","title":"x"}],"completionLogs":[],"checkIns":[],"medals":[]}"""
        assertEquals(BackupHealth.OK, bm().validateBackupJson(json))
    }

    @Test
    fun `无法解析的 JSON 校验为 CORRUPT`() {
        assertEquals(BackupHealth.CORRUPT, bm().validateBackupJson("这不是json{{{"))

    }

    @Test
    fun `版本号缺失或非法 校验为 CORRUPT`() {
        assertEquals(BackupHealth.CORRUPT, bm().validateBackupJson("{}"))
        assertEquals(BackupHealth.CORRUPT, bm().validateBackupJson("""{"schemaVersion":0}"""))
    }

    @Test
    fun `高于当前 schema 版本 校验为 CORRUPT`() {
        val json = """{"schemaVersion":999,"tasks":[{"id":"t1"}]}"""
        assertEquals(BackupHealth.CORRUPT, bm().validateBackupJson(json))
    }

    @Test
    fun `结构合法但无数据 校验为 EMPTY`() {
        val json = """{"app":"TickClear","schemaVersion":1,"exportedAt":0,"groups":[],"tasks":[],"completionLogs":[],"checkIns":[],"medals":[]}"""
        assertEquals(BackupHealth.EMPTY, bm().validateBackupJson(json))
    }
}
