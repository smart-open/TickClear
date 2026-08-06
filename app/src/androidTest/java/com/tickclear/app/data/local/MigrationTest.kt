package com.tickclear.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实迁移回放测试（仪器化，需 connectedAndroidTest 环境）。
 *
 * 校验每个显式 Migration 执行后产生的 schema 与 Room 导出的 schema JSON 完全一致，
 * 防止「改动实体却忘了改 Migration」这类静默破坏升级的回归。
 *
 * 覆盖范围说明：仓库保留了 5→10 的导出 schema（app/schemas/...），历史 1→4 的
 * schema JSON 未随仓库留存，故本测试只覆盖可校验的 5→10 区段。若后续需要覆盖 1→4，
 * 须先把对应版本的 schema JSON 找回/重建并复制到
 * app/src/androidTest/assets/databases/com.tickclear.app.data.local.AppDatabase/。
 *
 * 新增数据库版本时的固定动作：
 * 1. 把 app/schemas/.../N.json 复制到 androidTest assets 同名目录；
 * 2. 在此补一条 migrate(N-1)To(N) 用例，并把全链路用例的终点抬到 N。
 *
 * 运行：./gradlew :app:connectedAndroidTest --tests "*MigrationTest*"
 * （androidTest 为独立 source set，不影响 assembleDebug 与 unit test 回归。）
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val DB_NAME = AppDatabase::class.java.canonicalName
        ?: "com.tickclear.app.data.local.AppDatabase"
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DB_NAME,
    )

    @Test
    fun migrate5To6() = validate(5, 6)

    @Test
    fun migrate6To7() = validate(6, 7)

    @Test
    fun migrate7To8() = validate(7, 8)

    @Test
    fun migrate8To9() = validate(8, 9)

    @Test
    fun migrate9To10() = validate(9, 10)

    /** 全链路回放：模拟老用户从 v5 一路升到当前最高版本。 */
    @Test
    fun migrate5To10() {
        helper.createDatabase(TEST_DB, 5).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            AppDatabase.MIGRATIONS[4], // 5->6
            AppDatabase.MIGRATIONS[5], // 6->7
            AppDatabase.MIGRATIONS[6], // 7->8
            AppDatabase.MIGRATIONS[7], // 8->9
            AppDatabase.MIGRATIONS[8], // 9->10
        )
    }

    private fun validate(from: Int, to: Int) {
        val name = "db_$from"
        helper.createDatabase(name, from).close()
        helper.runMigrationsAndValidate(name, to, true, AppDatabase.MIGRATIONS[from - 1])
    }
}
