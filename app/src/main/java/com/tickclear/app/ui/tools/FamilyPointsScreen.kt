package com.tickclear.app.ui.tools

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 家庭成员积分仪（V2.9++ 生活助手）。
 * 成员 / 任务 / 奖励均支持增删改，并经 SharedPreferences 持久化（零新依赖，无 ViewModel）。
 * 完成任务给当前选中成员加分，积分可兑换奖励，也可手动加减；撤销上一步。
 * 成就徽章扩至 10 级，累计分数达标解锁。
 */

/** 待删除项的标识，供确认弹窗复用。 */
private data class DeleteTarget(val kind: String, val id: String, val name: String)

private data class Member(val id: String, val name: String)
private data class TaskItem(val id: String, val name: String, val points: Int)
private data class RewardItem(val id: String, val name: String, val cost: Int)

private val DEFAULT_MEMBERS = listOf(
    Member("m1", "小明"),
    Member("m2", "小红"),
)
private val DEFAULT_TASKS = listOf(
    TaskItem("t_tidy", "整理房间", 5),
    TaskItem("t_hw", "完成作业", 10),
    TaskItem("t_dish", "帮忙洗碗", 3),
    TaskItem("t_early", "早起打卡", 2),
    TaskItem("t_read", "阅读15分钟", 4),
    TaskItem("t_sport", "运动30分钟", 6),
)
private val DEFAULT_REWARDS = listOf(
    RewardItem("r_toy", "玩具", 50),
    RewardItem("r_milktea", "奶茶", 15),
    RewardItem("r_money", "零花钱10元", 20),
    RewardItem("r_park", "去公园", 8),
    RewardItem("r_movie", "看电影", 30),
)

/** 成就徽章（10 级）：累计分数达到阈值后解锁为彩色球；未解锁时灰化半透明。 */
private data class Badge(val id: String, val threshold: Int, val nameRes: Int, val color: Color)

private val BADGES = listOf(
    Badge("b1", 10, R.string.badge_novice_name, Color(0xFF66BB6A)),
    Badge("b2", 30, R.string.badge_progress_name, Color(0xFF42A5F5)),
    Badge("b3", 60, R.string.badge_excellent_name, Color(0xFFFFA726)),
    Badge("b4", 100, R.string.badge_outstanding_name, Color(0xFFAB47BC)),
    Badge("b5", 150, R.string.badge_role_model_name, Color(0xFFFFCA28)),
    Badge("b6", 210, R.string.badge_star_name, Color(0xFF26C6DA)),
    Badge("b7", 280, R.string.badge_champion_name, Color(0xFFEF5350)),
    Badge("b8", 360, R.string.badge_legend_name, Color(0xFF5C6BC0)),
    Badge("b9", 450, R.string.badge_master_name, Color(0xFFEC407A)),
    Badge("b10", 550, R.string.badge_grandmaster_name, Color(0xFFFFD700)),
)

/** 五角星路径（10 点交替）：中心 (cx,cy)，外半径 [outer]，内半径 [inner]。 */
private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val ang = -PI.toFloat() / 2f + i * (PI.toFloat() / points)
        val x = cx + r * cos(ang)
        val y = cy + r * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private const val PREFS_NAME = "family_points"
private const val NEW_ID = "__new__"

/** 名称中可能混进的分隔符先清掉，避免破坏存档的 `|`/`;` 结构。 */
private fun sanitize(s: String) = s.replace('|', ' ').replace(';', ' ')

private fun loadMembers(p: SharedPreferences): List<Member> =
    p.getString("members_v2", null)
        ?.takeIf { it.isNotEmpty() }
        ?.split(";")
        ?.mapNotNull { seg ->
            val a = seg.split("|")
            if (a.size == 2) Member(a[0], a[1]) else null
        }
        ?.takeIf { it.isNotEmpty() } ?: DEFAULT_MEMBERS

private fun saveMembers(p: SharedPreferences, list: List<Member>) {
    p.edit().putString(
        "members_v2",
        list.joinToString(";") { "${it.id}|${sanitize(it.name)}" },
    ).apply()
}

private fun loadTasks(p: SharedPreferences): List<TaskItem> =
    p.getString("tasks_v2", null)
        ?.takeIf { it.isNotEmpty() }
        ?.split(";")
        ?.mapNotNull { seg ->
            val a = seg.split("|")
            if (a.size == 3) TaskItem(a[0], a[1], a[2].toIntOrNull() ?: 0) else null
        }
        ?.takeIf { it.isNotEmpty() } ?: DEFAULT_TASKS

private fun saveTasks(p: SharedPreferences, list: List<TaskItem>) {
    p.edit().putString(
        "tasks_v2",
        list.joinToString(";") { "${it.id}|${sanitize(it.name)}|${it.points}" },
    ).apply()
}

private fun loadRewards(p: SharedPreferences): List<RewardItem> =
    p.getString("rewards_v2", null)
        ?.takeIf { it.isNotEmpty() }
        ?.split(";")
        ?.mapNotNull { seg ->
            val a = seg.split("|")
            if (a.size == 3) RewardItem(a[0], a[1], a[2].toIntOrNull() ?: 0) else null
        }
        ?.takeIf { it.isNotEmpty() } ?: DEFAULT_REWARDS

private fun saveRewards(p: SharedPreferences, list: List<RewardItem>) {
    p.edit().putString(
        "rewards_v2",
        list.joinToString(";") { "${it.id}|${sanitize(it.name)}|${it.cost}" },
    ).apply()
}

private fun loadScores(p: SharedPreferences, members: List<Member>): Map<String, Int> =
    members.associate { it.id to p.getInt("score_${it.id}", 0) }

private fun saveScore(p: SharedPreferences, id: String, score: Int) {
    p.edit().putInt("score_$id", score).apply()
}

/** 上一步操作，用于撤销：给 [memberId] 加了 [delta] 分（兑换为负数）。 */
private data class PointsAction(val memberId: String, val delta: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyPointsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 缓存 prefs 实例：每次加减分都 getSharedPreferences 会重复走一次 ContextImpl 查表。
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var members by remember { mutableStateOf(loadMembers(prefs)) }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var rewards by remember { mutableStateOf(loadRewards(prefs)) }
    var scores by remember { mutableStateOf(loadScores(prefs, members)) }
    var selectedId by remember { mutableStateOf(members.first().id) }
    // 家长按错任务按钮（尤其是给错了孩子）此前只能反复点其它按钮硬凑回去，这里补一步撤销。
    var lastAction by remember { mutableStateOf<PointsAction?>(null) }

    // 各类编辑弹窗状态
    var memberDlg by remember { mutableStateOf<Member?>(null) }
    var memberName by remember { mutableStateOf("") }
    var taskDlg by remember { mutableStateOf<TaskItem?>(null) }
    var taskName by remember { mutableStateOf("") }
    var taskPts by remember { mutableStateOf("") }
    var rewardDlg by remember { mutableStateOf<RewardItem?>(null) }
    var rewardName by remember { mutableStateOf("") }
    var rewardCost by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    fun persistMembers(next: List<Member>) {
        members = next
        saveMembers(prefs, next)
        if (selectedId !in next.map { it.id }) selectedId = next.firstOrNull()?.id ?: ""
    }

    fun adjust(memberId: String, delta: Int) {
        val next = ((scores[memberId] ?: 0) + delta).coerceAtLeast(0)
        scores = scores + (memberId to next)
        saveScore(prefs, memberId, next)
        lastAction = PointsAction(memberId, delta)
        Haptic.vibrate(context, if (delta >= 0) 18 else 14)
    }

    fun redeem(memberId: String, cost: Int, rewardName: String) {
        val cur = scores[memberId] ?: 0
        if (cur < cost) {
            Toast.makeText(context, R.string.points_not_enough, Toast.LENGTH_SHORT).show()
            return
        }
        adjust(memberId, -cost)
        Haptic.vibrate(context, 24)
        Toast.makeText(context, context.getString(R.string.points_redeemed, rewardName), Toast.LENGTH_SHORT).show()
    }

    fun undo() {
        val action = lastAction ?: return
        adjust(action.memberId, -action.delta)
        lastAction = null
        Haptic.vibrate(context, 12)
        Toast.makeText(context, R.string.points_undone, Toast.LENGTH_SHORT).show()
    }

    // ——— 成员编辑 ———
    fun openAddMember() { memberName = ""; memberDlg = Member(NEW_ID, "") }
    fun openEditMember(m: Member) { memberName = m.name; memberDlg = m }
    fun confirmMember() {
        val name = memberName.trim()
        val dlg = memberDlg ?: return
        if (name.isEmpty()) return
        if (dlg.id == NEW_ID) {
            val id = "m_${System.currentTimeMillis()}"
            saveScore(prefs, id, 0)
            persistMembers(members + Member(id, name))
        } else {
            persistMembers(members.map { if (it.id == dlg.id) it.copy(name = name) else it })
        }
        memberDlg = null
    }

    // ——— 任务编辑 ———
    fun openAddTask() { taskName = ""; taskPts = ""; taskDlg = TaskItem(NEW_ID, "", 0) }
    fun openEditTask(t: TaskItem) { taskName = t.name; taskPts = t.points.toString(); taskDlg = t }
    fun confirmTask() {
        val name = taskName.trim()
        val pts = taskPts.toIntOrNull() ?: 0
        val dlg = taskDlg ?: return
        if (name.isEmpty() || pts <= 0) return
        if (dlg.id == NEW_ID) {
            tasks = tasks + TaskItem("t_${System.currentTimeMillis()}", name, pts)
        } else {
            tasks = tasks.map { if (it.id == dlg.id) it.copy(name = name, points = pts) else it }
        }
        saveTasks(prefs, tasks)
        taskDlg = null
    }

    // ——— 奖励编辑 ———
    fun openAddReward() { rewardName = ""; rewardCost = ""; rewardDlg = RewardItem(NEW_ID, "", 0) }
    fun openEditReward(r: RewardItem) { rewardName = r.name; rewardCost = r.cost.toString(); rewardDlg = r }
    fun confirmReward() {
        val name = rewardName.trim()
        val cost = rewardCost.toIntOrNull() ?: 0
        val dlg = rewardDlg ?: return
        if (name.isEmpty() || cost <= 0) return
        if (dlg.id == NEW_ID) {
            rewards = rewards + RewardItem("r_${System.currentTimeMillis()}", name, cost)
        } else {
            rewards = rewards.map { if (it.id == dlg.id) it.copy(name = name, cost = cost) else it }
        }
        saveRewards(prefs, rewards)
        rewardDlg = null
    }

    fun askDelete(kind: String, id: String, name: String) {
        deleteTarget = DeleteTarget(kind, id, name)
    }

    fun doDelete() {
        val t = deleteTarget ?: return
        when (t.kind) {
            "member" -> {
                persistMembers(members.filter { it.id != t.id })
                prefs.edit().remove("score_${t.id}").apply()
                scores = scores - t.id
            }
            "task" -> {
                tasks = tasks.filter { it.id != t.id }
                saveTasks(prefs, tasks)
            }
            "reward" -> {
                rewards = rewards.filter { it.id != t.id }
                saveRewards(prefs, rewards)
            }
        }
        deleteTarget = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.points_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.points_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 成员：选择当前计分对象 + 手动加减 + 增删改
            SectionCard(title = stringResource(R.string.points_members)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { openAddMember() }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.points_add_member))
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    members.forEach { m ->
                        val score = scores[m.id] ?: 0
                        val selected = selectedId == m.id
                        Card(
                            onClick = { selectedId = m.id },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            border = if (selected) {
                                null
                            } else {
                                androidx.compose.material3.CardDefaults.outlinedCardBorder()
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        m.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        stringResource(R.string.points_score) + " " + score,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { adjust(m.id, -1) }) {
                                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.points_manual_sub), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { adjust(m.id, 1) }) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.points_manual_add), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { openEditMember(m) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.points_edit_member), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { askDelete("member", m.id, m.name) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.points_delete_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 任务：点击给当前成员加分，支持增删改
            SectionCard(title = stringResource(R.string.points_tasks)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { openAddTask() }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.points_add_task))
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    tasks.forEach { t ->
                        Button(onClick = { adjust(selectedId, t.points) }) {
                            Text(stringResource(R.string.points_task_chip, t.name, t.points))
                        }
                        IconButton(onClick = { openEditTask(t) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.points_edit_task), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { askDelete("task", t.id, t.name) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.points_delete_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 奖励：当前成员兑换，支持增删改
            SectionCard(title = stringResource(R.string.points_rewards)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { openAddReward() }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.points_add_reward))
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rewards.forEach { r ->
                        val affordable = (scores[selectedId] ?: 0) >= r.cost
                        val rewardNameStr = r.name
                        Button(
                            onClick = { redeem(selectedId, r.cost, rewardNameStr) },
                            enabled = affordable,
                        ) {
                            Text(stringResource(R.string.points_reward_label, r.name, r.cost))
                        }
                        IconButton(onClick = { openEditReward(r) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.points_edit_reward), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { askDelete("reward", r.id, r.name) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.points_delete_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 成就徽章（10 级）
            SectionCard(title = stringResource(R.string.points_achievements_title)) {
                val totalScore = scores[selectedId] ?: 0
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    BADGES.forEach { b ->
                        AchievementBadge(
                            unlocked = totalScore >= b.threshold,
                            color = b.color,
                            name = stringResource(b.nameRes),
                            thresholdLabel = "≥${b.threshold}",
                        )
                    }
                }
            }

            lastAction?.let { action ->
                val memberNameStr = members.firstOrNull { it.id == action.memberId }?.name ?: ""
                OutlinedButton(
                    onClick = { undo() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.points_undo) +
                            " · " + memberNameStr + " " + (if (action.delta > 0) "+" else "") + action.delta,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }

    // ——— 成员编辑弹窗 ———
    memberDlg?.let { dlg ->
        AlertDialog(
            onDismissRequest = { memberDlg = null },
            confirmButton = {
                TextButton(onClick = { confirmMember() }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { memberDlg = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            title = { Text(stringResource(if (dlg.id == NEW_ID) R.string.points_add_member else R.string.points_edit_member)) },
            text = {
                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    label = { Text(stringResource(R.string.points_member_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    // ——— 任务编辑弹窗 ———
    taskDlg?.let { dlg ->
        AlertDialog(
            onDismissRequest = { taskDlg = null },
            confirmButton = {
                TextButton(onClick = { confirmTask() }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { taskDlg = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            title = { Text(stringResource(if (dlg.id == NEW_ID) R.string.points_add_task else R.string.points_edit_task)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = { taskName = it },
                        label = { Text(stringResource(R.string.points_task_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taskPts,
                        onValueChange = { taskPts = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.points_task_points)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    // ——— 奖励编辑弹窗 ———
    rewardDlg?.let { dlg ->
        AlertDialog(
            onDismissRequest = { rewardDlg = null },
            confirmButton = {
                TextButton(onClick = { confirmReward() }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { rewardDlg = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            title = { Text(stringResource(if (dlg.id == NEW_ID) R.string.points_add_reward else R.string.points_edit_reward)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = rewardName,
                        onValueChange = { rewardName = it },
                        label = { Text(stringResource(R.string.points_reward_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rewardCost,
                        onValueChange = { rewardCost = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.points_reward_cost)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    // ——— 删除确认 ———
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = { doDelete() }) { Text(stringResource(R.string.points_delete_item)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.points_delete_item)) },
            text = { Text(stringResource(R.string.points_delete_item_confirm, t.name)) },
        )
    }
}

/** 可维护项的外层卡片（标题 + 内容）。 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/**
 * 单个成就徽章（V2.9++ 二巡）：受光球体 + 五角星。未解锁时整体灰化半透明，
 * 静态绘制（零常驻帧循环），守电池红线。
 */
@Composable
private fun AchievementBadge(
    unlocked: Boolean,
    color: Color,
    name: String,
    thresholdLabel: String,
) {
    val sphere = if (unlocked) color else Color(0xFF9E9E9E).copy(alpha = 0.40f)
    val labelColor = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val starFill = if (unlocked) Color.White else Color(0xFFBDBDBD)
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = s * 0.34f
                drawSoftShadow(Offset(cx, cy + r * 1.02f), r * 0.78f, r * 0.18f, 0.22f)
                fillSphere(Offset(cx, cy), r, sphere, rimLight = false)
                drawRimLight(Offset(cx, cy), r, sphere.lighten(0.6f), 0.38f)
                drawGloss(Offset(cx - r * 0.34f, cy - r * 0.40f), r * 0.26f, r * 0.16f, 0.40f)
                val star = starPath(cx, cy, r * 0.52f, r * 0.22f)
                drawPath(path = star, color = starFill)
            }
        }
        Text(
            text = if (unlocked) name else stringResource(R.string.points_locked),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = thresholdLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
