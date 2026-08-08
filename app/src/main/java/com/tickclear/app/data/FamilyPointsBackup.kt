package com.tickclear.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 家庭积分仪备份助手（V2.9++，零新依赖）。
 *
 * 该工具数据存于独立 `SharedPreferences("family_points")`（非主 DataStore、非 Room），
 * 此前任何备份都不含，导致「一键导出全部数据」在家庭积分上出现真实缺口。
 * 此处复用 [FamilyPointsScreen] 的存档键格式（members_v2 / tasks_v2 / rewards_v2 / score_<id>），
 * 导出为可随全量备份落盘的 JSON，并在导入时原样写回。
 *
 * 仅备份用户数据，默认种子（DEFAULT_MEMBERS 等）由界面在缺省时回退，无需导出。
 */
object FamilyPointsBackup {

    private const val PREFS_NAME = "family_points"
    private const val KEY_MEMBERS = "members_v2"
    private const val KEY_TASKS = "tasks_v2"
    private const val KEY_REWARDS = "rewards_v2"
    private const val KEY_SCORE_PREFIX = "score_"

    /** 导出当前家庭积分数据；无数据时返回空 JSON 对象（导入侧据此跳过）。 */
    fun export(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        obj.put(KEY_MEMBERS, p.getString(KEY_MEMBERS, "") ?: "")
        obj.put(KEY_TASKS, p.getString(KEY_TASKS, "") ?: "")
        obj.put(KEY_REWARDS, p.getString(KEY_REWARDS, "") ?: "")

        val scores = JSONObject()
        p.getString(KEY_MEMBERS, "")?.takeIf { it.isNotEmpty() }?.split(";")
            ?.forEach { seg ->
                val id = seg.split("|").firstOrNull() ?: return@forEach
                scores.put(id, p.getInt(KEY_SCORE_PREFIX + id, 0))
            }
        obj.put("scores", scores)
        return obj
    }

    /** 从备份还原家庭积分数据；字段缺失时保持本地现状（不覆盖清空）。 */
    fun restore(context: Context, obj: JSONObject) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = p.edit()
        if (obj.has(KEY_MEMBERS)) edit.putString(KEY_MEMBERS, obj.optString(KEY_MEMBERS))
        if (obj.has(KEY_TASKS)) edit.putString(KEY_TASKS, obj.optString(KEY_TASKS))
        if (obj.has(KEY_REWARDS)) edit.putString(KEY_REWARDS, obj.optString(KEY_REWARDS))
        obj.optJSONObject("scores")?.let { scores ->
            val it = scores.keys()
            while (it.hasNext()) {
                val id = it.next()
                if (id.isNotBlank()) edit.putInt(KEY_SCORE_PREFIX + id, scores.optInt(id))
            }
        }
        edit.apply()
    }
}
