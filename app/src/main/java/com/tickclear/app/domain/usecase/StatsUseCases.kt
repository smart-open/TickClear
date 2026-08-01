package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import com.tickclear.app.domain.log.AppLogger

/** 「未分组」分组在统计柱状图中的哨兵 id；UI 据其取 strings.xml 的 tasks_no_group，避免 domain 层硬编码中文（红线）。 */
const val UNGROUPED_GROUP_ID = "__ungrouped__"
data class DailyStat(val date: String, val completed: Int)
data class GroupStat(val groupName: String, val completed: Int, val total: Int)
data class TaskStats(
    val totalCompleted: Int,
    val todayCompleted: Int,
    val todayTotal: Int,
    val completionRate: Float,
    val streakDays: Int,
    val byGroup: List<GroupStat>,
    val daily: List<DailyStat>,
)

@Singleton
class GetStatsUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val completionRepository: CompletionRepository,
    private val groupRepository: GroupRepository,
    private val instanceRepository: TaskInstanceRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TaskStats> =
        taskRepository.observeAll().flatMapLatest { tasks ->
            val today = LocalDate.now()
            // 懒生成当日实例（幂等），复用流中 tasks 避免二次全量查询（L4 性能）；
            // ensureInstancesForDate 可能因底层存储异常而失败，该失败不应中断整页统计，
            // 降级为「不保证当日实例完整」继续聚合（todayTotal 可能偏低）。
            val todayInstancesFlow = flow {
                runCatching { instanceRepository.ensureInstancesForDate(today, tasks) }
                    .onFailure { AppLogger.e("GetStatsUseCase", "ensureInstancesForDate failed, todayTotal may be low", it) }
                // ⚠️ 必须持续订阅实例/完成日志（combine + emitAll），不能 .first() 快照：
                // 完成重复任务只写 task_instance/completion_log、不改 task 表，
                // 快照模式下外层 observeAll 不重发 → 统计页「今日完成/连续/累计」停滞、
                // 且与热力图（由 completionsFlow 驱动）自相矛盾（P2 根因）。
                emitAll(instanceRepository.observeOn(today))
            }
            // 三个响应式源任一变化即重算：completion_log（累计/连续/热力图）、
            // 当日实例（今日完成/完成率）、活动分组（分组统计）。
            combine(
                completionRepository.observeAll(),
                todayInstancesFlow,
                groupRepository.observeActive(),
            ) { completions, todayInstances, groups ->
                runCatching {
                    val todayDone = todayInstances.count { it.status == TaskStatus.COMPLETED.code }
                    val todayTotal = todayInstances.size
                    val todayRate = if (todayTotal > 0) todayDone.toFloat() / todayTotal else 0f

                    // 连续天数基于 CompletionLog 日期（PRD §7.3）
                    val streak = StreakUtils.computeStreak(completions.map { it.dateLocal })

                    val byGroup = buildList {
                        groups.forEach { g ->
                            val gt = tasks.filter { it.deletedAt == null && it.groupId == g.id }
                            add(GroupStat(g.name, gt.count { it.status == TaskStatus.COMPLETED.code }, gt.size))
                        }
                        val ungrouped = tasks.filter {
                            it.deletedAt == null && it.groupId == null && RepeatType.fromCode(it.repeatType) == RepeatType.NONE
                        }
                        if (ungrouped.isNotEmpty()) {
                            add(GroupStat(UNGROUPED_GROUP_ID, ungrouped.count { it.status == TaskStatus.COMPLETED.code }, ungrouped.size))
                        }
                    }

                    val daily = (0 until 30).map { offset ->
                        val ds = today.minusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        DailyStat(ds, completions.count { it.dateLocal == ds })
                    }.reversed()

                    TaskStats(
                        totalCompleted = completions.size,
                        todayCompleted = todayDone,
                        todayTotal = todayTotal,
                        completionRate = todayRate,
                        streakDays = streak,
                        byGroup = byGroup,
                        daily = daily,
                    )
                }.getOrElse {
                    // 统计聚合失败（多为底层 Room 查询/实例生成异常）不应让统计页崩溃，
                    // 降级为安全空状态；真实异常已打 logcat 供定位根因。
                    AppLogger.e("GetStatsUseCase", "stats compute failed, fallback safe", it)
                    TaskStats(0, 0, 0, 0f, 0, emptyList(), emptyList())
                }
            }
        }
}

/** 评估并解锁满足条件的勋章，返回本次新解锁的 key 列表。 */
@Singleton
class CheckMedalsUseCase @Inject constructor(
    private val completionRepository: CompletionRepository,
    private val checkInRepository: CheckInRepository,
    private val taskRepository: TaskRepository,
    private val groupRepository: GroupRepository,
    private val medalRepository: MedalRepository,
    private val instanceRepository: TaskInstanceRepository,
) {
    suspend operator fun invoke(): List<String> {
        val completions = completionRepository.observeAll().first()
        val totalCompleted = completions.size
        val streak = StreakUtils.computeStreak(completions.map { it.dateLocal })

        // 定时任务：带具体时间设定的任务（scheduledStartMin 非空）。用于「准时达人」判定。
        val tasks = taskRepository.observeAll().first()
        val timedTaskIds = tasks.filter { it.scheduledStartMin != null }.map { it.id }.toSet()
        val timedCompletions = completions.count { it.taskId in timedTaskIds }
        // 单日最多完成数：用于「雷厉风行」判定。
        val bestDayCount = completions.groupBy { it.dateLocal }.maxOfOrNull { it.value.size } ?: 0

        val today = LocalDate.now()
        instanceRepository.ensureInstancesForDate(today)
        val todayInstances = instanceRepository.observeOn(today).first()
        val todayTotal = todayInstances.size
        val todayDone = todayInstances.count { it.status == TaskStatus.COMPLETED.code }

        val ym = today.format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0, 7)
        val groupsActive = groupRepository.observeActive().first().size

        val unlocked = mutableListOf<String>()
        suspend fun tryUnlock(key: String, cond: Boolean) {
            if (cond && !medalRepository.isUnlocked(key)) {
                medalRepository.unlock(key)
                unlocked += key
            }
        }
        tryUnlock("FIRST_TASK", totalCompleted >= 1)
        tryUnlock("STREAK_3", streak >= 3)
        tryUnlock("STREAK_7", streak >= 7)
        tryUnlock("LIGHTNING", bestDayCount >= 5)
        tryUnlock("ONTIME_10", timedCompletions >= 10)
        tryUnlock("RATE_100", todayTotal > 0 && todayDone == todayTotal)
        tryUnlock("GROUPS_3", groupsActive >= 3)
        tryUnlock("MONTH_MVP", completions.count { it.dateLocal.startsWith(ym) } >= 30)
        return unlocked
    }
}

/** 若今日任务全部完成，记一次打卡（不允许补卡）。基于当日实例判定。 */
@Singleton
class RecordCheckInUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val checkInRepository: CheckInRepository,
    private val instanceRepository: TaskInstanceRepository,
) {
    suspend operator fun invoke() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (checkInRepository.getByDate(todayStr) != null) return
        val today = LocalDate.now()
        instanceRepository.ensureInstancesForDate(today)
        val instances = instanceRepository.observeOn(today).first()
        if (instances.isEmpty()) return
        val allDone = instances.all { it.status == TaskStatus.COMPLETED.code }
        if (allDone) checkInRepository.checkIn(todayStr)
    }
}
