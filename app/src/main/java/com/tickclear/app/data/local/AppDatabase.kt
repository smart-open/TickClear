package com.tickclear.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tickclear.app.data.local.dao.CheckInDao
import com.tickclear.app.data.local.dao.CompletionLogDao
import com.tickclear.app.data.local.dao.MedalUnlockDao
import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.data.local.dao.TaskInstanceDao
import com.tickclear.app.data.local.dao.VoiceHistoryDao
import com.tickclear.app.data.local.dao.VoiceMemoDao
import com.tickclear.app.data.local.dao.HabitCheckInDao
import com.tickclear.app.data.local.dao.HabitDao
import com.tickclear.app.data.local.dao.ExpiryDao
import com.tickclear.app.data.local.entities.CheckInEntity
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import com.tickclear.app.data.local.entities.VoiceMemoEntity
import com.tickclear.app.data.local.entities.HabitEntity
import com.tickclear.app.data.local.entities.HabitCheckInEntity
import com.tickclear.app.data.local.entities.ExpiryEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TaskGroupEntity::class,
        TaskEntity::class,
        TaskInstanceEntity::class,
        CompletionLogEntity::class,
        MedalUnlockEntity::class,
        CheckInEntity::class,
        VoiceHistoryEntity::class,
        HabitEntity::class,
        HabitCheckInEntity::class,
        VoiceMemoEntity::class,
        ExpiryEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskGroupDao(): TaskGroupDao
    abstract fun taskDao(): TaskDao
    abstract fun taskInstanceDao(): TaskInstanceDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun medalUnlockDao(): MedalUnlockDao
    abstract fun checkInDao(): CheckInDao
    abstract fun voiceHistoryDao(): VoiceHistoryDao
    abstract fun voiceMemoDao(): VoiceMemoDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCheckInDao(): HabitCheckInDao
    abstract fun expiryDao(): ExpiryDao

    companion object {
        private const val DB_NAME = "tickclear.db"
        private val sqlcipherLock = Any()
        @Volatile private var sqlcipherLoaded = false

        /** v1 → v2：首版实体集稳定，无结构性变更（仅版本号递增）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { /* 空迁移：v1 与 v2 schema 一致 */ }
        }

        /** v2 → v3：同上，无结构性变更。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { /* 空迁移：v2 与 v3 schema 一致 */ }
        }

        /** v3 → v4：为 task 新增位置提醒三列（地理围栏）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN geo_lat REAL")
                db.execSQL("ALTER TABLE task ADD COLUMN geo_lng REAL")
                db.execSQL("ALTER TABLE task ADD COLUMN geo_radius INTEGER")
            }
        }

        /**
         * v4 → v5：
         *  - task 新增 repeat_interval_hours（每 N 小时重复，PRD §7 自定义间隔）；
         *  - task_instance 索引由 (taskId, dueDateLocal) 扩展到 (taskId, dueDateLocal, dueMinute)，
         *    支持子日级重复（每 N 小时）同一天多个实例；
         *  - V2.56 修正：v5 实体另声明单列 dueDateLocal 索引，迁移须显式补建，
         *    否则 v4→v5 升级后实例表缺该索引，与导出 5.json 校验不一致导致崩溃。
         *  所有 DROP/CREATE 均用 IF NOT EXISTS，幂等可重复执行。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN repeat_interval_hours INTEGER")
                // 旧 2 列索引（taskId, dueDateLocal）→ 3 列唯一索引
                db.execSQL("DROP INDEX IF EXISTS index_task_instance_taskId_dueDateLocal")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_task_instance_taskId_dueDateLocal_dueMinute " +
                        "ON task_instance(taskId, dueDateLocal, dueMinute)",
                )
                // 补建单列 dueDateLocal 索引，对齐 v5 实体声明
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_instance_dueDateLocal ON task_instance(dueDateLocal)")
                // 单列 taskId 索引：v5 实体声明要求；v4 升级时本由 v1 遗留保留，
                // 此处显式补建使迁移自包含（不依赖历史索引残留），幂等安全。
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_instance_taskId ON task_instance(taskId)")
            }
        }

        /** v5 → v6：新增 voice_history 表（V2.65 语音历史）。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        created_at INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        text TEXT NOT NULL,
                        kind TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v6 → v7：为 task 新增 tags 列（V2.67 任务标签，CSV 存储）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 → v8：新增 habit（习惯定义）与 habit_checkin（每日打卡）两表（V2.69 习惯养成模式）。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        repeatDays TEXT NOT NULL,
                        reminderMin INTEGER NOT NULL,
                        colorIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        archived INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_checkin (
                        habitId TEXT NOT NULL,
                        dateLocal TEXT NOT NULL,
                        checkedAt INTEGER NOT NULL,
                        PRIMARY KEY(habitId, dateLocal)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v8 → v9：新增 voice_memos 表（V2.9 工具箱 · 语音备忘录）。 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CREATE 语句须与 VoiceMemoEntity 生成的建表语句逐字一致，
                // 否则 Room 的迁移校验（expected vs migrated schema）会报错。
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_memos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v9 → v10：新增 expiry_reminders 表（V2.9++ 工具箱 · 到期提醒）。 */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CREATE 语句须与 ExpiryEntity 生成的建表语句逐字一致，
                // 否则 Room 的迁移校验（expected vs migrated schema）会报错。
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS expiry_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        expire_epoch_day INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        reminder_enabled INTEGER NOT NULL,
                        reminder_days_before INTEGER NOT NULL,
                        recurring INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 全部显式迁移，供仪器化契约测试（MigrationTestHelper）引用，无需新增依赖。 */
        internal val MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10,
            )

        fun create(context: Context, passphrase: String): AppDatabase {
            // L2：sqlcipher-android 需在打开数据库前显式加载 native 库（该 artifact 不自动加载）。
            // 加单次守卫，避免重复加载抛 UnsatisfiedLinkError。
            synchronized(sqlcipherLock) {
                if (!sqlcipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlcipherLoaded = true
                }
            }
            val factory = SupportOpenHelperFactory(passphrase.toByteArray())
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                // 刻意不启用 fallbackToDestructiveMigration：版本升级且缺显式 Migration 时，
                // Room 会显式抛异常（而非静默清空用户数据）。上线前必须在此追加 addMigrations(...)，
                // 严禁在未提供 Migration 的情况下 bump schema version。
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                .build()
        }
    }
}
