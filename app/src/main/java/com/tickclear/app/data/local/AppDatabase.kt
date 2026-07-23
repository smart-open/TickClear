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
import com.tickclear.app.data.local.entities.CheckInEntity
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TaskGroupEntity::class,
        TaskEntity::class,
        TaskInstanceEntity::class,
        CompletionLogEntity::class,
        MedalUnlockEntity::class,
        CheckInEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskGroupDao(): TaskGroupDao
    abstract fun taskDao(): TaskDao
    abstract fun taskInstanceDao(): TaskInstanceDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun medalUnlockDao(): MedalUnlockDao
    abstract fun checkInDao(): CheckInDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
