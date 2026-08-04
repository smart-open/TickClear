package com.tickclear.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tickclear.app.data.local.dao.CompletionLogDao
import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskInstanceDao
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 核心流程冒烟（V2.47，CI 真机/模拟器通道）：
 * 在真实 SQLCipher + Room 数据库上走通「建任务 → 生成当日实例 → 完成 → 读回一致」，
 * 覆盖应用核心数据路径的集成回归（native 库加载、DAO 往返、状态机一致性、完成幂等）。
 *
 * 不引入任何新依赖（复用已声明的 androidx.room / androidx.junit / sqlcipher）。
 * 该测试是 V2.47「核心流程冒烟」在模拟器通道的可自动化部分；人工真机走查仍结转
 * （见 docs/成熟度评估.md、docs/测试与验收清单.md）。
 */
@RunWith(AndroidJUnit4::class)
class CoreFlowSmokeInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var ctx: Context
    private lateinit var taskDao: TaskDao
    private lateinit var instanceDao: TaskInstanceDao
    private lateinit var completionDao: CompletionLogDao

    private val today: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory("instrumented-test-passphrase".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        taskDao = db.taskDao()
        instanceDao = db.taskInstanceDao()
        completionDao = db.completionLogDao()
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
    fun core_flow_create_instance_complete_and_readback() {
        runBlocking {
            val taskId = "smoke-task-1"
            val instanceId = "$taskId@$today"

            // 1) 建一个「一次性(每日)」任务
            taskDao.insert(
                TaskEntity(
                    id = taskId,
                    title = "喝水",
                    repeatType = "NONE",
                    allDay = true,
                ),
            )
            assertNotNull("任务应已写入", taskDao.getActiveById(taskId))

            // 2) 生成当日实例（视图/调度懒生成路径的等价写入）
            instanceDao.upsert(
                TaskInstanceEntity(id = instanceId, taskId = taskId, dueDateLocal = today),
            )
            val dayInstances = instanceDao.observeOn(today).first()
            assertEquals("当日应有 1 个实例", 1, dayInstances.size)
            assertEquals(instanceId, dayInstances.first().id)

            // 3) 完成：CompletionLog 幂等写入 + 实例置完成 + 任务终态（NONE 任务）
            completionDao.insert(
                CompletionLogEntity(
                    id = instanceId,
                    taskId = taskId,
                    completedAt = System.currentTimeMillis(),
                    dateLocal = today,
                ),
            )
            instanceDao.setCompleted(instanceId)
            taskDao.setStatus(taskId, 2, System.currentTimeMillis())

            // 4) 读回一致性校验
            assertEquals("当日完成数应为 1", 1, completionDao.countByDate(today))
            val inst = instanceDao.get(taskId, today)
            assertNotNull("实例应存在", inst)
            assertEquals("实例应标记为完成", 2, inst!!.status)
            val task = taskDao.getActiveById(taskId)
            assertNotNull("任务仍应可读（仅终态，未软删）", task)
            assertEquals("任务应标记为完成", 2, task!!.status)
        }
    }

    @Test
    fun completion_is_idempotent_for_same_day() {
        runBlocking {
            val taskId = "smoke-task-2"
            val instanceId = "$taskId@$today"

            taskDao.insert(TaskEntity(id = taskId, title = "吃药", repeatType = "NONE", allDay = true))
            instanceDao.upsert(TaskInstanceEntity(id = instanceId, taskId = taskId, dueDateLocal = today))

            // 首次完成
            completionDao.insert(
                CompletionLogEntity(id = instanceId, taskId = taskId, completedAt = 1L, dateLocal = today),
            )
            instanceDao.setCompleted(instanceId)

            // 重复勾选同一天（B3 幂等：CompletionLog 唯一约束 IGNORE 去重，实例不重复置完成）
            completionDao.insert(
                CompletionLogEntity(id = instanceId, taskId = taskId, completedAt = 2L, dateLocal = today),
            )
            instanceDao.setCompleted(instanceId)

            assertEquals("重复勾选不应新增完成记录（幂等）", 1, completionDao.countByDate(today))
            val inst = instanceDao.get(taskId, today)
            assertNotNull(inst)
            assertEquals(2, inst!!.status)
        }
    }

    @Test
    fun recycle_bin_soft_delete_and_restore() {
        runBlocking {
            val taskId = "smoke-task-3"
            taskDao.insert(TaskEntity(id = taskId, title = "读书", repeatType = "NONE", allDay = true))

            // 软删（进回收站）
            taskDao.softDelete(taskId)
            assertNull("软删后 getActiveById 应返回 null", taskDao.getActiveById(taskId))

            // 回收站恢复
            taskDao.restore(taskId)
            assertNotNull("恢复后应可再次读回", taskDao.getActiveById(taskId))
        }
    }

    private companion object {
        // 使用独立测试库名，避免 deleteDatabase 误删真实生产库（tickclear.db）。
        private const val DB_NAME = "tickclear-instrumented-test.db"
    }
}
