package com.tickclear.app.domain.backup

import com.tickclear.app.data.local.entities.CheckInEntity
import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.data.local.entities.MedalUnlockEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.ExpiryRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.HabitRepository
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
    val habits: Int,
    val habitCheckIns: Int,
    val expiries: Int = 0,
    val taskInstances: Int = 0,
)

/**
 * 数据备份管理：导出/导入为 JSON（org.json，零额外依赖）。
 *
 * - 导出：任务组 / 任务 / 任务实例 / 完成日志 / 打卡 / 勋章 / 习惯 / 到期提醒 + 版本与时间戳。
 * - 导入：按主键 upsert 合并（不清空现有数据），冲突写入策略沿用各 DAO（REPLACE/IGNORE）。
 * - 失败统一抛 [AppException]，UI 侧映射为用户可读提示。
 *
 * 有意不纳入备份的两张表（避免恢复出「坏数据」）：
 * - `voice_memos`：只存音频元数据，真实录音在应用私有目录且不随备份走，
 *   恢复后会得到一堆点开就报错的死链条目，不如不导。
 * - `voice_history`：默认关闭的对话流水，属临时日志且含敏感原文，不入明文/可分享备份。
 */
@Singleton
class BackupManager @Inject constructor(
    private val taskRepository: TaskRepository,
    private val groupRepository: GroupRepository,
    private val completionRepository: CompletionRepository,
    private val checkInRepository: CheckInRepository,
    private val medalRepository: MedalRepository,
    private val habitRepository: HabitRepository,
    private val txn: TransactionRunner,
    private val expiryRepository: ExpiryRepository,
    private val taskInstanceRepository: TaskInstanceRepository,
) {
    companion object {
        /**
         * 备份结构版本。
         * - v1：groups / tasks / completionLogs / checkIns / medals / habits / habitCheckIns
         * - v2：新增 expiries（到期提醒）、taskInstances（重复任务的完成/跳过态）。
         *   v1 备份可被 v2 直接读取（缺失数组按空处理）；v2 备份在旧版本上会被判为版本过高而拒绝，
         *   这是刻意为之——避免旧版静默丢弃这两类数据。
         */
        const val SCHEMA_VERSION = 2
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
            // V2.70 设备间本地迁移：习惯定义与打卡一并导出，保证跨设备迁移完整。
            val habits = habitRepository.observeHabits().first()
            val habitCheckIns = habits.flatMap { h ->
                habitRepository.getCheckinDates(h.id).map { d -> h.id to d }
            }
            // V2.9++ 到期提醒属纯用户数据，此前遗漏导致换机后会员/续费到期日全丢。
            val expiries = expiryRepository.observeAll().first()
            // 重复任务的完成态存在实例表而非任务表，不导出会让恢复后所有重复任务重新变「未完成」。
            val taskInstances = taskInstanceRepository.allWithState()

            JSONObject().apply {
                put(KEY_APP, "TickClear")
                put(KEY_VERSION, SCHEMA_VERSION)
                put(KEY_EXPORTED_AT, System.currentTimeMillis())
                put("groups", JSONArray().apply { groups.forEach { put(groupToJson(it)) } })
                put("tasks", JSONArray().apply { tasks.forEach { put(taskToJson(it)) } })
                put("completionLogs", JSONArray().apply { completions.forEach { put(completionToJson(it)) } })
                put("checkIns", JSONArray().apply { checkIns.forEach { put(checkInToJson(it)) } })
                put("medals", JSONArray().apply { medals.forEach { put(medalToJson(it)) } })
                put("habits", JSONArray().apply { habits.forEach { put(habitToJson(it)) } })
                put("habitCheckIns", JSONArray().apply { habitCheckIns.forEach { (hid, d) -> put(habitCheckInToJson(hid, d)) } })
                put("expiries", JSONArray().apply { expiries.forEach { put(expiryToJson(it)) } })
                put("taskInstances", JSONArray().apply { taskInstances.forEach { put(instanceToJson(it)) } })
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
        // 版本兼容迁移：旧备份按主键合并导入前，先将其结构升级到当前 schema。
        migrateIfNeeded(root, version)

        val groupsArr = root.optJSONArray("groups") ?: JSONArray()
        val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
        val completionsArr = root.optJSONArray("completionLogs") ?: JSONArray()
        val checkInsArr = root.optJSONArray("checkIns") ?: JSONArray()
        val medalsArr = root.optJSONArray("medals") ?: JSONArray()
        // V2.70：习惯数组缺失时（旧备份）按空处理，向前兼容。
        val habitsArr = root.optJSONArray("habits") ?: JSONArray()
        val habitCheckInsArr = root.optJSONArray("habitCheckIns") ?: JSONArray()
        // v2 新增；v1 旧备份缺失时按空处理，向前兼容。
        val expiriesArr = root.optJSONArray("expiries") ?: JSONArray()
        val instancesArr = root.optJSONArray("taskInstances") ?: JSONArray()

        if (groupsArr.length() == 0 && tasksArr.length() == 0 &&
            completionsArr.length() == 0 && checkInsArr.length() == 0 &&
            medalsArr.length() == 0 && habitsArr.length() == 0 && habitCheckInsArr.length() == 0 &&
            expiriesArr.length() == 0 && instancesArr.length() == 0
        ) {
            throw AppException(ErrorCode.IMPORT_EMPTY)
        }

        try {
            // 有效组 id 集合 = 备份内组 ∪ 现有活跃组，置于事务外计算：
            // 既避免「事务内嵌套读」在 SQLCipher 单连接池下死锁/ANR（P1），
            // 也保证「组与任务同备份」时任务引用的组不被误置空。
            val importedGroupIds = (0 until groupsArr.length()).mapNotNull { gi ->
                runCatching { groupsArr.getJSONObject(gi).getString("id") }.getOrNull()
            }.toSet()
            val existingGroupIds = groupRepository.observeActive().first().map { it.id }.toSet()
            val validGroupIds = importedGroupIds + existingGroupIds

            // 任务实例同理：taskId 指向不存在的任务时直接丢弃，避免恢复出永远不显示的孤儿实例。
            val validTaskIds = if (instancesArr.length() == 0) {
                emptySet()
            } else {
                (0 until tasksArr.length()).mapNotNull { ti ->
                    runCatching { tasksArr.getJSONObject(ti).getString("id") }.getOrNull()
                }.toSet() + taskRepository.observeAll().first().map { it.id }.toSet()
            }

            // 整体包裹在数据库事务中（R5）：任一写入失败整体回滚，避免留下部分导入脏数据。
            txn.run {
                // 先导入组，再导入任务（外键 groupId 依赖组）。
                for (i in 0 until groupsArr.length()) {
                    groupRepository.upsert(jsonToGroup(groupsArr.getJSONObject(i)))
                }
                for (i in 0 until tasksArr.length()) {
                    val t = jsonToTask(tasksArr.getJSONObject(i))
                    // groupId 指向不存在的组时置空，避免外键冲突。
                    val safe = if (t.groupId != null && t.groupId !in validGroupIds) t.copy(groupId = null) else t
                    taskRepository.upsert(safe)
                }
                // V2.70：习惯定义先于其打卡写入。
                for (i in 0 until habitsArr.length()) {
                    habitRepository.createHabit(jsonToHabit(habitsArr.getJSONObject(i)))
                }
                for (i in 0 until completionsArr.length()) {
                    completionRepository.insert(jsonToCompletion(completionsArr.getJSONObject(i)))
                }
                for (i in 0 until checkInsArr.length()) {
                    checkInRepository.upsert(jsonToCheckIn(checkInsArr.getJSONObject(i)))
                }
                for (i in 0 until habitCheckInsArr.length()) {
                    val (hid, d) = jsonToHabitCheckIn(habitCheckInsArr.getJSONObject(i))
                    habitRepository.checkIn(hid, d)
                }
                for (i in 0 until medalsArr.length()) {
                    medalRepository.upsert(jsonToMedal(medalsArr.getJSONObject(i)))
                }
                // 实例依赖任务，放在任务写入之后；REPLACE 覆盖本地懒生成的空实例，保住完成态。
                for (i in 0 until instancesArr.length()) {
                    val inst = jsonToInstance(instancesArr.getJSONObject(i))
                    if (inst.taskId in validTaskIds) taskInstanceRepository.restore(inst)
                }
                for (i in 0 until expiriesArr.length()) {
                    expiryRepository.insert(jsonToExpiry(expiriesArr.getJSONObject(i)))
                }
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
            habits = habitsArr.length(),
            habitCheckIns = habitCheckInsArr.length(),
            expiries = expiriesArr.length(),
            taskInstances = instancesArr.length(),
        )
    }

    /**
     * 导出为加密字节（V2.6）：明文 JSON → [BackupCrypto] 信封。
     * 自动备份默认走此路径；手动导出仍可用 [exportToJson] 明文。
     */
    suspend fun exportEncrypted(): ByteArray = withContext(Dispatchers.IO) {
        BackupCrypto.encrypt(exportToJson().toByteArray(Charsets.UTF_8))
    }

    /**
     * 从字节导入（V2.6）：自动识别加密信封（[BackupCrypto.isEncrypted]），
     * 命中则解密后走 [importFromJson]，否则按旧明文 JSON 兼容导入。
     */
    suspend fun importEncrypted(bytes: ByteArray): ImportResult = withContext(Dispatchers.IO) {
        val json = BackupCrypto.decrypt(bytes).toString(Charsets.UTF_8)
        importFromJson(json)
    }

    /**
     * 导出单个任务组为可分享模板（V2.71）：含该组及其任务，可经 SAF 保存后分享，
     * 在另一设备通过 [importFromJson]（按主键合并）重建。
     * 模板仅含 groups + tasks，其余数组缺省，导入时按「空数组」兼容处理。
     */
    suspend fun exportGroupTemplate(groupId: String): String = withContext(Dispatchers.IO) {
        try {
            val group = groupRepository.getById(groupId)
                ?: throw AppException(ErrorCode.EXPORT_WRITE_FAILED, IllegalStateException("group not found: $groupId"))
            val tasks = taskRepository.observeByGroup(groupId).first()
            JSONObject().apply {
                put(KEY_APP, "TickClear")
                put(KEY_VERSION, SCHEMA_VERSION)
                put(KEY_EXPORTED_AT, System.currentTimeMillis())
                put("type", "groupTemplate")
                put("groups", JSONArray().apply { put(groupToJson(group)) })
                put("tasks", JSONArray().apply { tasks.forEach { put(taskToJson(it)) } })
            }.toString(2)
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(ErrorCode.EXPORT_WRITE_FAILED, e)
        }
    }

    /**
     * 备份健康校验（V2.23 自愈校验）：纯解析 + 版本 + 结构校验，不落盘、不依赖 Android 上下文，
     * 供自动备份写入后即时自检与设置页回显。JVM 单测可覆盖。
     *
     * 规则对齐 [importFromJson]：版本缺失/非法（≤0）或高于当前 schema（不支持）视为 [BackupHealth.CORRUPT]；
     * 结构合法但五类数据全空视为 [BackupHealth.EMPTY]；否则 [BackupHealth.OK]。
     */
    fun validateBackupJson(json: String): BackupHealth {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return BackupHealth.CORRUPT
        val version = root.optInt(KEY_VERSION, -1)
        if (version <= 0) return BackupHealth.CORRUPT
        if (version > SCHEMA_VERSION) return BackupHealth.CORRUPT
        val total = (root.optJSONArray("groups")?.length() ?: 0) +
            (root.optJSONArray("tasks")?.length() ?: 0) +
            (root.optJSONArray("completionLogs")?.length() ?: 0) +
            (root.optJSONArray("checkIns")?.length() ?: 0) +
            (root.optJSONArray("medals")?.length() ?: 0) +
            (root.optJSONArray("habits")?.length() ?: 0) +
            (root.optJSONArray("habitCheckIns")?.length() ?: 0) +
            (root.optJSONArray("expiries")?.length() ?: 0) +
            (root.optJSONArray("taskInstances")?.length() ?: 0)
        return if (total == 0) BackupHealth.EMPTY else BackupHealth.OK
    }

    /**
     * 备份结构迁移（V2.6 / 版本化）：将 [root] 从 [from] 版本逐步升级到 [SCHEMA_VERSION]。
     * 后续新增字段/重命名时在此追加 when 分支，保证旧备份向前兼容，避免破坏性导入。
     */
    private fun migrateIfNeeded(root: JSONObject, from: Int) {
        var v = from
        while (v < SCHEMA_VERSION) {
            when (v) {
                // 1 -> 2：仅新增 expiries / taskInstances 两个数组，
                // 缺失时读取侧已按空数组兜底，无需改写结构。
                1 -> Unit
                else -> Unit
            }
            v++
        }
    }

    // ── 序列化 ──
    private fun groupToJson(g: TaskGroup) = JSONObject().apply {
        put("id", g.id); put("name", g.name); put("icon", g.icon); put("colorKey", g.colorKey)
        put("orderIndex", g.orderIndex); put("repeatType", g.repeatType)
        put("repeatAnchorMin", g.repeatAnchorMin ?: JSONObject.NULL)
        put("status", g.status); put("createdAt", g.createdAt); put("updatedAt", g.updatedAt)
        put("deletedAt", g.deletedAt ?: JSONObject.NULL)
    }

    private fun taskToJson(t: Task) = JSONObject().apply {
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
        put("repeatIntervalHours", t.repeatIntervalHours ?: JSONObject.NULL)
        put("geoLat", t.geoLat ?: JSONObject.NULL)
        put("geoLng", t.geoLng ?: JSONObject.NULL)
        put("geoRadius", t.geoRadius ?: JSONObject.NULL)
        put("source", t.source); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
        put("tags", JSONArray().apply { t.tags.forEach { put(it) } })
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

    // ── V2.70 习惯序列化（设备间本地迁移）──
    private fun habitToJson(h: Habit) = JSONObject().apply {
        put("id", h.id); put("title", h.title); put("emoji", h.emoji)
        put("repeatDays", h.repeatDays); put("reminderMin", h.reminderMin)
        put("colorIndex", h.colorIndex); put("createdAt", h.createdAt)
        put("archived", h.archived); put("orderIndex", h.orderIndex)
    }

    private fun habitCheckInToJson(habitId: String, date: String) = JSONObject().apply {
        put("habitId", habitId); put("dateLocal", date)
    }

    // ── v2 新增序列化：到期提醒 + 任务实例 ──
    private fun expiryToJson(e: ExpiryEntity) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("category", e.category)
        put("expireEpochDay", e.expireEpochDay); put("note", e.note)
        put("reminderEnabled", e.reminderEnabled); put("reminderDaysBefore", e.reminderDaysBefore)
        put("recurring", e.recurring); put("createdAt", e.createdAt)
    }

    private fun instanceToJson(i: TaskInstanceEntity) = JSONObject().apply {
        put("id", i.id); put("taskId", i.taskId); put("dueDateLocal", i.dueDateLocal)
        put("dueMinute", i.dueMinute ?: JSONObject.NULL)
        put("status", i.status)
        put("completedAt", i.completedAt ?: JSONObject.NULL)
        put("source", i.source); put("createdAt", i.createdAt)
    }

    // ── 反序列化 ──
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)

    private fun jsonToGroup(o: JSONObject) = TaskGroup(
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

    private fun jsonToTask(o: JSONObject) = Task(
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
        repeatIntervalHours = o.optIntOrNull("repeatIntervalHours"),
        geoLat = o.optDoubleOrNull("geoLat"),
        geoLng = o.optDoubleOrNull("geoLng"),
        geoRadius = o.optIntOrNull("geoRadius"),
        reminderEnabled = o.optBoolean("reminderEnabled", false),
        reminderLevel = o.optString("reminderLevel", "mid"),
        reminderOffsetMin = o.optIntOrNull("reminderOffsetMin"),
        source = o.optString("source", "manual"),
        tags = o.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it, null)?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        } ?: emptyList(),
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

    private fun jsonToHabit(o: JSONObject) = Habit(
        id = o.getString("id"),
        title = o.optString("title", ""),
        emoji = o.optString("emoji", ""),
        repeatDays = o.optString("repeatDays", "1,2,3,4,5,6,7"),
        reminderMin = o.optInt("reminderMin", -1),
        colorIndex = o.optInt("colorIndex", 0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        archived = o.optBoolean("archived", false),
        orderIndex = o.optInt("orderIndex", 0),
    )

    private fun jsonToHabitCheckIn(o: JSONObject) = o.getString("habitId") to o.getString("dateLocal")

    private fun jsonToExpiry(o: JSONObject) = ExpiryEntity(
        id = o.optLong("id", 0L),
        title = o.optString("title", ""),
        category = o.optString("category", ""),
        expireEpochDay = o.optLong("expireEpochDay", 0L),
        note = o.optString("note", ""),
        reminderEnabled = o.optBoolean("reminderEnabled", true),
        reminderDaysBefore = o.optInt("reminderDaysBefore", 1),
        recurring = o.optBoolean("recurring", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun jsonToInstance(o: JSONObject) = TaskInstanceEntity(
        id = o.getString("id"),
        taskId = o.optString("taskId", ""),
        dueDateLocal = o.optString("dueDateLocal", ""),
        dueMinute = o.optIntOrNull("dueMinute"),
        status = o.optInt("status", 0),
        completedAt = o.optLongOrNull("completedAt"),
        source = o.optString("source", "manual"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )
}
