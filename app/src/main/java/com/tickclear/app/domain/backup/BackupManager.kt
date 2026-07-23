package com.tickclear.app.domain.backup

import com.tickclear.app.data.local.entities.CheckInEntity
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskGroupEntity
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 导入结果统计（用于给用户回执）。 */
data class ImportResult(
    val groups: Int,
    val tasks: Int,
    val completions: Int,
    val checkIns: Int,
    val medals: Int,
)

/**
 * 数据备份管理：导出/导入为 JSON（org.json，零额外依赖）。
 *
 * - 导出：任务组 / 任务 / 完成日志 / 打卡 / 勋章 + 版本与时间戳。
 * - 导入：按主键 upsert 合并（不清空现有数据），冲突写入策略沿用各 DAO（REPLACE/IGNORE）。
 * - 失败统一抛 [AppException]，UI 侧映射为用户可读提示。
 */
@Singleton
class BackupManager @Inject constructor(
    private val taskRepository: TaskRepository,
    private val groupRepository: GroupRepository,
    private val completionRepository: CompletionRepository,
    private val checkInRepository: CheckInRepository,
    private val medalRepository: MedalRepository,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        private const val KEY_VERSION = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_APP = "app"
    }

    /** 生成备份 JSON 字符串。 */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        try {
            val groups = groupRepository.observeActive().first()
            val tasks = taskRepository.observeAll().first()
            val completions = completionRepository.observeAll().first()
            val checkIns = checkInRepository.getAll()
            val medals = medalRepository.all()

            JSONObject().apply {
                put(KEY_APP, "TickClear")
                put(KEY_VERSION, SCHEMA_VERSION)
                put(KEY_EXPORTED_AT, System.currentTimeMillis())
                put("groups", JSONArray().apply { groups.forEach { put(groupToJson(it)) } })
                put("tasks", JSONArray().apply { tasks.forEach { put(taskToJson(it)) } })
                put("completionLogs", JSONArray().apply { completions.forEach { put(completionToJson(it)) } })
                put("checkIns", JSONArray().apply { checkIns.forEach { put(checkInToJson(it)) } })
                put("medals", JSONArray().apply { medals.forEach { put(medalToJson(it)) } })
            }.toString(2)
        } catch (e: Exception) {
            throw AppException(ErrorCode.EXPORT_WRITE_FAILED, e)
        }
    }

    /** 解析并合并导入。返回各类计数。 */
    suspend fun importFromJson(json: String): ImportResult = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw AppException(ErrorCode.IMPORT_PARSE_FAILED, e)
        }
        val version = root.optInt(KEY_VERSION, -1)
        if (version <= 0) throw AppException(ErrorCode.IMPORT_PARSE_FAILED)
        if (version > SCHEMA_VERSION) throw AppException(ErrorCode.IMPORT_VERSION_UNSUPPORTED)

        val groupsArr = root.optJSONArray("groups") ?: JSONArray()
        val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
        val completionsArr = root.optJSONArray("completionLogs") ?: JSONArray()
        val checkInsArr = root.optJSONArray("checkIns") ?: JSONArray()
        val medalsArr = root.optJSONArray("medals") ?: JSONArray()

        if (groupsArr.length() == 0 && tasksArr.length() == 0 &&
            completionsArr.length() == 0 && checkInsArr.length() == 0 && medalsArr.length() == 0
        ) {
            throw AppException(ErrorCode.IMPORT_EMPTY)
        }

        try {
            // 先导入组，再导入任务（外键 groupId 依赖组）。
            for (i in 0 until groupsArr.length()) {
                groupRepository.upsert(jsonToGroup(groupsArr.getJSONObject(i)))
            }
            val validGroupIds = groupRepository.observeActive().first().map { it.id }.toSet()
            for (i in 0 until tasksArr.length()) {
                val t = jsonToTask(tasksArr.getJSONObject(i))
                // groupId 指向不存在的组时置空，避免外键冲突。
                val safe = if (t.groupId != null && t.groupId !in validGroupIds) t.copy(groupId = null) else t
                taskRepository.upsert(safe)
            }
            for (i in 0 until completionsArr.length()) {
                completionRepository.insert(jsonToCompletion(completionsArr.getJSONObject(i)))
            }
            for (i in 0 until checkInsArr.length()) {
                checkInRepository.upsert(jsonToCheckIn(checkInsArr.getJSONObject(i)))
            }
            for (i in 0 until medalsArr.length()) {
                medalRepository.upsert(jsonToMedal(medalsArr.getJSONObject(i)))
            }
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(ErrorCode.IMPORT_PARSE_FAILED, e)
        }

        ImportResult(
            groups = groupsArr.length(),
            tasks = tasksArr.length(),
            completions = completionsArr.length(),
            checkIns = checkInsArr.length(),
            medals = medalsArr.length(),
        )
    }

    // ── 序列化 ──
    private fun groupToJson(g: TaskGroupEntity) = JSONObject().apply {
        put("id", g.id); put("name", g.name); put("icon", g.icon); put("colorKey", g.colorKey)
        put("orderIndex", g.orderIndex); put("repeatType", g.repeatType)
        put("repeatAnchorMin", g.repeatAnchorMin ?: JSONObject.NULL)
        put("status", g.status); put("createdAt", g.createdAt); put("updatedAt", g.updatedAt)
        put("deletedAt", g.deletedAt ?: JSONObject.NULL)
    }

    private fun taskToJson(t: TaskEntity) = JSONObject().apply {
        put("id", t.id); put("groupId", t.groupId ?: JSONObject.NULL); put("title", t.title)
        put("notes", t.notes); put("status", t.status)
        put("scheduledStartMin", t.scheduledStartMin ?: JSONObject.NULL)
        put("scheduledEndMin", t.scheduledEndMin ?: JSONObject.NULL)
        put("allDay", t.allDay); put("scheduledDate", t.scheduledDate ?: JSONObject.NULL)
        put("repeatType", t.repeatType)
        put("repeatIntervalDays", t.repeatIntervalDays ?: JSONObject.NULL)
        put("repeatWeekdays", t.repeatWeekdays ?: JSONObject.NULL)
        put("repeatMonthDay", t.repeatMonthDay ?: JSONObject.NULL)
        put("repeatAnchorMin", t.repeatAnchorMin ?: JSONObject.NULL)
        put("repeatAnchorDate", t.repeatAnchorDate ?: JSONObject.NULL)
        put("reminderEnabled", t.reminderEnabled); put("reminderLevel", t.reminderLevel)
        put("reminderOffsetMin", t.reminderOffsetMin ?: JSONObject.NULL)
        put("source", t.source); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
        put("completedAt", t.completedAt ?: JSONObject.NULL)
        put("deletedAt", t.deletedAt ?: JSONObject.NULL)
    }

    private fun completionToJson(c: CompletionLogEntity) = JSONObject().apply {
        put("id", c.id); put("taskId", c.taskId); put("completedAt", c.completedAt)
        put("dateLocal", c.dateLocal); put("source", c.source)
    }

    private fun checkInToJson(c: CheckInEntity) = JSONObject().apply {
        put("dateLocal", c.dateLocal); put("checkedAt", c.checkedAt)
    }

    private fun medalToJson(m: MedalUnlockEntity) = JSONObject().apply {
        put("medalKey", m.medalKey); put("unlockedAt", m.unlockedAt)
    }

    // ── 反序列化 ──
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

    private fun jsonToGroup(o: JSONObject) = TaskGroupEntity(
        id = o.getString("id"),
        name = o.optString("name", ""),
        icon = o.optString("icon", "📁"),
        colorKey = o.optString("colorKey", "blue"),
        orderIndex = o.optInt("orderIndex", 0),
        repeatType = o.optString("repeatType", "NONE"),
        repeatAnchorMin = o.optIntOrNull("repeatAnchorMin") ?: 540,
        status = o.optInt("status", 0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    private fun jsonToTask(o: JSONObject) = TaskEntity(
        id = o.getString("id"),
        groupId = o.optStringOrNull("groupId"),
        title = o.optString("title", ""),
        notes = o.optString("notes", ""),
        status = o.optInt("status", 0),
        scheduledStartMin = o.optIntOrNull("scheduledStartMin"),
        scheduledEndMin = o.optIntOrNull("scheduledEndMin"),
        allDay = o.optBoolean("allDay", false),
        scheduledDate = o.optStringOrNull("scheduledDate"),
        repeatType = o.optString("repeatType", "NONE"),
        repeatIntervalDays = o.optIntOrNull("repeatIntervalDays"),
        repeatWeekdays = o.optStringOrNull("repeatWeekdays"),
        repeatMonthDay = o.optIntOrNull("repeatMonthDay"),
        repeatAnchorMin = o.optIntOrNull("repeatAnchorMin"),
        repeatAnchorDate = o.optStringOrNull("repeatAnchorDate"),
        reminderEnabled = o.optBoolean("reminderEnabled", false),
        reminderLevel = o.optString("reminderLevel", "mid"),
        reminderOffsetMin = o.optIntOrNull("reminderOffsetMin"),
        source = o.optString("source", "manual"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        completedAt = o.optLongOrNull("completedAt"),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    private fun jsonToCompletion(o: JSONObject) = CompletionLogEntity(
        id = o.getString("id"),
        taskId = o.getString("taskId"),
        completedAt = o.optLong("completedAt", System.currentTimeMillis()),
        dateLocal = o.getString("dateLocal"),
        source = o.optString("source", "manual"),
    )

    private fun jsonToCheckIn(o: JSONObject) = CheckInEntity(
        dateLocal = o.getString("dateLocal"),
        checkedAt = o.optLong("checkedAt", System.currentTimeMillis()),
    )

    private fun jsonToMedal(o: JSONObject) = MedalUnlockEntity(
        medalKey = o.getString("medalKey"),
        unlockedAt = o.optLong("unlockedAt", System.currentTimeMillis()),
    )
}
