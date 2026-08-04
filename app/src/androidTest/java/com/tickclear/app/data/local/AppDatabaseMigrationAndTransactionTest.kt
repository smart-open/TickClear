package com.tickclear.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.domain.backup.RoomTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 仪器化契约测试（CI 真机/模拟器通道）：
 * - 在真实设备上打开 SQLCipher + Room 数据库（校验 native 库加载、Room 打开、DAO 往返）；
 * - 验证 R5 事务原子性：RoomTransactionRunner 成功提交、失败整体回滚；
 * - 验证 R8 目标：task_instance 三索引（含 taskId 单列）在真实库上存在。
 *
 * 不引入任何新依赖（复用已声明的 androidx.room.testing / androidx.junit / sqlcipher）。
 * 迁移链路（1→5）的逐版本回放受限于仓库仅提交了 5.json 导出 schema，
 * 此处以「真实库打开 + 索引结构断言」间接保证 schema 与导出一致（见 docs 测试与验收清单.md）。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationAndTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory("instrumented-test-passphrase".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ctx.deleteDatabase(DB_NAME)
        }
    }

    @Test
    fun database_opens_and_round_trips_task_group() {
        runBlocking {
            db.taskGroupDao().insert(TaskGroupEntity(id = "g1", name = "Group One"))
            val active = db.taskGroupDao().observeActive().first()
            assertTrue(active.any { it.id == "g1" && it.name == "Group One" })
        }
    }

    @Test
    fun transactionRunner_commits_on_success() {
        runBlocking {
            val runner = RoomTransactionRunner(db)
            runner.run { db.taskGroupDao().insert(TaskGroupEntity(id = "commit-g", name = "Commit")) }
            val active = db.taskGroupDao().observeActive().first()
            assertTrue("事务成功应提交写入", active.any { it.id == "commit-g" })
        }
    }

    @Test
    fun transactionRunner_rolls_back_on_failure() {
        runBlocking {
            val runner = RoomTransactionRunner(db)
            try {
                runner.run {
                    db.taskGroupDao().insert(TaskGroupEntity(id = "rollback-g", name = "Rollback"))
                    throw RuntimeException("force rollback")
                }
            } catch (_: RuntimeException) {
                // 预期：事务应整体回滚，异常向上传播
            }
            val active = db.taskGroupDao().observeActive().first()
            assertFalse("事务内异常应整体回滚，不应残留部分写入", active.any { it.id == "rollback-g" })
        }
    }

    @Test
    fun taskInstance_has_expected_indexes() {
        val indexes = db.query(SimpleSQLiteQuery("PRAGMA index_list(task_instance)")).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) {
                names.add(c.getString(c.getColumnIndexOrThrow("name")))
            }
            names
        }
        assertTrue("缺失单列 taskId 索引（R8 修复目标）", "index_task_instance_taskId" in indexes)
        assertTrue("缺失单列 dueDateLocal 索引", "index_task_instance_dueDateLocal" in indexes)
        assertTrue(
            "缺失唯一三列索引",
            "index_task_instance_taskId_dueDateLocal_dueMinute" in indexes,
        )
    }

    private companion object {
        // 使用独立测试库名，避免 deleteDatabase 误删真实生产库（tickclear.db）。
        private const val DB_NAME = "tickclear-instrumented-test.db"
    }
}
