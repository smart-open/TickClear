package com.tickclear.app.ui.tools

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing

/**
 * 家庭成员积分仪（V2.9++ 生活助手）。
 * 内置成员 / 任务 / 奖励数据；完成任务给当前成员加分，积分可兑换奖励。
 * 分数经 SharedPreferences 持久化（零新依赖），无 ViewModel。
 */
private data class Member(val id: String, val nameRes: Int)
private data class TaskItem(val id: String, val nameRes: Int, val points: Int)
private data class RewardItem(val id: String, val nameRes: Int, val cost: Int)

private val MEMBERS = listOf(
    Member("child1", R.string.points_member_child1),
    Member("child2", R.string.points_member_child2),
)
private val TASKS = listOf(
    TaskItem("tidy", R.string.points_task_tidy, 5),
    TaskItem("homework", R.string.points_task_homework, 10),
    TaskItem("dish", R.string.points_task_dish, 3),
    TaskItem("early", R.string.points_task_early, 2),
    TaskItem("read", R.string.points_task_read, 4),
    TaskItem("sport", R.string.points_task_sport, 6),
)
private val REWARDS = listOf(
    RewardItem("toy", R.string.points_reward_toy, 50),
    RewardItem("milktea", R.string.points_reward_milktea, 15),
    RewardItem("money", R.string.points_reward_money, 20),
    RewardItem("park", R.string.points_reward_park, 8),
    RewardItem("movie", R.string.points_reward_movie, 30),
)

private const val PREFS_NAME = "family_points"

private fun loadScores(prefs: SharedPreferences): Map<String, Int> =
    MEMBERS.associate { it.id to prefs.getInt("score_${it.id}", 0) }

private fun saveScore(prefs: SharedPreferences, id: String, score: Int) {
    prefs.edit().putInt("score_$id", score).apply()
}

/** 上一步操作，用于撤销：给 [memberId] 加了 [delta] 分（兑换为负数）。 */
private data class PointsAction(val memberId: String, val delta: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyPointsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 缓存 prefs 实例：每次加减分都 getSharedPreferences 会重复走一次 ContextImpl 查表。
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedId by remember { mutableStateOf(MEMBERS.first().id) }
    var scores by remember { mutableStateOf(loadScores(prefs)) }
    // 家长按错任务按钮（尤其是给错了孩子）此前只能反复点其它按钮硬凑回去，这里补一步撤销。
    var lastAction by remember { mutableStateOf<PointsAction?>(null) }

    fun applyDelta(memberId: String, delta: Int) {
        val next = ((scores[memberId] ?: 0) + delta).coerceAtLeast(0)
        scores = scores + (memberId to next)
        saveScore(prefs, memberId, next)
    }

    fun addPoints(memberId: String, pts: Int) {
        applyDelta(memberId, pts)
        lastAction = PointsAction(memberId, pts)
        Haptic.vibrate(context, 18)
    }

    fun redeem(memberId: String, cost: Int, rewardName: String) {
        val cur = scores[memberId] ?: 0
        if (cur < cost) {
            Toast.makeText(context, R.string.points_not_enough, Toast.LENGTH_SHORT).show()
            return
        }
        applyDelta(memberId, -cost)
        lastAction = PointsAction(memberId, -cost)
        Haptic.vibrate(context, 24)
        Toast.makeText(context, context.getString(R.string.points_redeemed, rewardName), Toast.LENGTH_SHORT).show()
    }

    fun undo() {
        val action = lastAction ?: return
        applyDelta(action.memberId, -action.delta)
        lastAction = null
        Haptic.vibrate(context, 12)
        Toast.makeText(context, R.string.points_undone, Toast.LENGTH_SHORT).show()
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
            // 成员切换 + 当前分数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                MEMBERS.forEach { m ->
                    val score = scores[m.id] ?: 0
                    FilterChip(
                        selected = selectedId == m.id,
                        onClick = { selectedId = m.id },
                        label = {
                            Text(stringResource(m.nameRes) + " · " + stringResource(R.string.points_score) + " " + score)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            lastAction?.let { action ->
                val memberName = stringResource(MEMBERS.first { it.id == action.memberId }.nameRes)
                OutlinedButton(
                    onClick = { undo() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.points_undo) +
                            " · " + memberName + " " + (if (action.delta > 0) "+" else "") + action.delta,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.points_tasks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TASKS.forEach { t ->
                            Button(onClick = { addPoints(selectedId, t.points) }) {
                                Text(stringResource(t.nameRes) + " +" + t.points)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.points_rewards),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        REWARDS.forEach { r ->
                            val affordable = (scores[selectedId] ?: 0) >= r.cost
                            val rewardName = stringResource(r.nameRes)
                            Button(
                                onClick = { redeem(selectedId, r.cost, rewardName) },
                                enabled = affordable,
                            ) {
                                Text(stringResource(R.string.points_reward_label, rewardName, r.cost))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}
