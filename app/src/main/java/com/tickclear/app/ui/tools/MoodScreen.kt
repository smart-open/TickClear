package com.tickclear.app.ui.tools

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.min

/** [tint] 为脸部基色：按情绪冷暖取色，配合受光渐变让分布图/列表一眼可辨。 */
private data class MoodDef(val code: Int, val tint: Color, val labelRes: Int)

private val MOODS = listOf(
    MoodDef(1, Color(0xFFFFC93C), R.string.mood_1), // 开心：明亮暖黄
    MoodDef(2, Color(0xFFFFDD8A), R.string.mood_2), // 平静：柔和浅黄
    MoodDef(3, Color(0xFFCBBE95), R.string.mood_3), // 疲惫：低饱和土黄
    MoodDef(4, Color(0xFFFFAE5C), R.string.mood_4), // 焦虑：躁动橙
    MoodDef(5, Color(0xFF9FB6D6), R.string.mood_5), // 难过：冷调蓝
    MoodDef(6, Color(0xFFEF5350), R.string.mood_6), // 生气：强烈红
    MoodDef(7, Color(0xFFB39DDB), R.string.mood_7), // 惊喜：明亮紫
    MoodDef(8, Color(0xFFB0BEC5), R.string.mood_8), // 不管啦：中性蓝灰
)

/** 五官墨色：统一暖褐，比纯黑柔和、与暖色脸更协调。 */
private val MOOD_INK = Color(0xFF4A3B2A)

/**
 * 3D 情绪表情（V2.9++ 美化）：用 [fillSphere] 受光球 + [drawSoftShadow] 接地阴影 + [drawGloss] 光泽
 * 替代原先的平涂 Unicode emoji——emoji 在不同 ROM 字体下样式漂移且毫无体积感。
 *
 * 五种情绪靠「基色冷暖 + 眉眼/嘴形」双通道区分，纯静态绘制（无动画、不耗电）。
 */
@Composable
fun MoodFace(code: Int, diameter: Dp, modifier: Modifier = Modifier) {
    val def = MOODS.firstOrNull { it.code == code } ?: MOODS[1]
    Canvas(modifier = modifier.size(diameter)) {
        val s = min(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.42f
        val lw = (r * 0.16f).coerceAtLeast(1.5f)
        val ink = MOOD_INK

        // 头部：接地软阴影 → 受光球 → 材质边缘光 → 左上光泽
        drawSoftShadow(Offset(cx, cy + r * 1.02f), r * 0.82f, r * 0.20f, 0.20f)
        fillSphere(Offset(cx, cy), r, def.tint, rimLight = false)
        drawRimLight(Offset(cx, cy), r, def.tint.lighten(0.6f), 0.42f)
        drawGloss(Offset(cx - r * 0.34f, cy - r * 0.40f), r * 0.30f, r * 0.20f, 0.42f)

        val eyeY = cy - r * 0.16f
        val eyeDx = r * 0.36f
        val eyeR = r * 0.11f

        when (code) {
            // 开心：圆眼 + 大笑弧（弧向下鼓 = 笑）
            1 -> {
                drawCircle(ink, eyeR, Offset(cx - eyeDx, eyeY))
                drawCircle(ink, eyeR, Offset(cx + eyeDx, eyeY))
                drawArc(
                    color = ink,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.46f, cy - r * 0.16f),
                    size = Size(r * 0.92f, r * 0.78f),
                    style = Stroke(width = lw, cap = StrokeCap.Round),
                )
            }
            // 平静：小圆眼 + 浅浅微笑
            2 -> {
                drawCircle(ink, eyeR * 0.85f, Offset(cx - eyeDx, eyeY))
                drawCircle(ink, eyeR * 0.85f, Offset(cx + eyeDx, eyeY))
                drawArc(
                    color = ink,
                    startAngle = 30f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.34f, cy + r * 0.04f),
                    size = Size(r * 0.68f, r * 0.40f),
                    style = Stroke(width = lw, cap = StrokeCap.Round),
                )
            }
            // 疲惫：半阖的横线眼 + 略歪的平嘴
            3 -> {
                drawLine(
                    ink,
                    Offset(cx - eyeDx - eyeR * 1.4f, eyeY),
                    Offset(cx - eyeDx + eyeR * 1.4f, eyeY),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx + eyeDx - eyeR * 1.4f, eyeY),
                    Offset(cx + eyeDx + eyeR * 1.4f, eyeY),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx - r * 0.24f, cy + r * 0.34f),
                    Offset(cx + r * 0.24f, cy + r * 0.42f),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
            }
            // 焦虑：圆眼 + 内高外低的八字眉 + 小幅下撇嘴
            4 -> {
                drawCircle(ink, eyeR, Offset(cx - eyeDx, eyeY))
                drawCircle(ink, eyeR, Offset(cx + eyeDx, eyeY))
                drawLine(
                    ink,
                    Offset(cx - eyeDx - r * 0.20f, eyeY - r * 0.28f),
                    Offset(cx - eyeDx + r * 0.16f, eyeY - r * 0.46f),
                    strokeWidth = lw * 0.8f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx + eyeDx + r * 0.20f, eyeY - r * 0.28f),
                    Offset(cx + eyeDx - r * 0.16f, eyeY - r * 0.46f),
                    strokeWidth = lw * 0.8f,
                    cap = StrokeCap.Round,
                )
                drawArc(
                    color = ink,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.30f, cy + r * 0.26f),
                    size = Size(r * 0.60f, r * 0.34f),
                    style = Stroke(width = lw, cap = StrokeCap.Round),
                )
            }
            // 生气：圆眼 + 内低外高八字眉 + 下撇嘴
            6 -> {
                drawCircle(ink, eyeR, Offset(cx - eyeDx, eyeY))
                drawCircle(ink, eyeR, Offset(cx + eyeDx, eyeY))
                drawLine(
                    ink,
                    Offset(cx - eyeDx - r * 0.20f, eyeY - r * 0.20f),
                    Offset(cx - eyeDx + r * 0.16f, eyeY - r * 0.46f),
                    strokeWidth = lw * 0.85f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx + eyeDx + r * 0.20f, eyeY - r * 0.20f),
                    Offset(cx + eyeDx - r * 0.16f, eyeY - r * 0.46f),
                    strokeWidth = lw * 0.85f,
                    cap = StrokeCap.Round,
                )
                drawArc(
                    color = ink,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.36f, cy + r * 0.22f),
                    size = Size(r * 0.72f, r * 0.40f),
                    style = Stroke(width = lw, cap = StrokeCap.Round),
                )
            }
            // 惊喜：瞪大的圆眼 + 挑高的眉 + 小张嘴（受光小圆）
            7 -> {
                drawCircle(ink, eyeR * 1.25f, Offset(cx - eyeDx, eyeY - r * 0.04f))
                drawCircle(ink, eyeR * 1.25f, Offset(cx + eyeDx, eyeY - r * 0.04f))
                drawLine(
                    ink,
                    Offset(cx - eyeDx - r * 0.18f, eyeY - r * 0.52f),
                    Offset(cx - eyeDx + r * 0.18f, eyeY - r * 0.52f),
                    strokeWidth = lw * 0.7f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx + eyeDx - r * 0.18f, eyeY - r * 0.52f),
                    Offset(cx + eyeDx + r * 0.18f, eyeY - r * 0.52f),
                    strokeWidth = lw * 0.7f,
                    cap = StrokeCap.Round,
                )
                fillSphere(
                    Offset(cx, cy + r * 0.40f),
                    r * 0.16f,
                    ink,
                    rimLight = false,
                )
            }
            // 不管啦：半阖横线眼 + 平直嘴（中性、无所谓）
            8 -> {
                drawLine(
                    ink,
                    Offset(cx - eyeDx - eyeR * 1.4f, eyeY),
                    Offset(cx - eyeDx + eyeR * 1.4f, eyeY),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx + eyeDx - eyeR * 1.4f, eyeY),
                    Offset(cx + eyeDx + eyeR * 1.4f, eyeY),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(cx - r * 0.22f, cy + r * 0.36f),
                    Offset(cx + r * 0.22f, cy + r * 0.36f),
                    strokeWidth = lw,
                    cap = StrokeCap.Round,
                )
            }
            // 难过（默认兜底）：圆眼 + 大幅下撇嘴 + 一颗受光泪珠
            else -> {
                drawCircle(ink, eyeR, Offset(cx - eyeDx, eyeY))
                drawCircle(ink, eyeR, Offset(cx + eyeDx, eyeY))
                drawArc(
                    color = ink,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.40f, cy + r * 0.24f),
                    size = Size(r * 0.80f, r * 0.46f),
                    style = Stroke(width = lw, cap = StrokeCap.Round),
                )
                fillSphere(
                    Offset(cx + eyeDx + r * 0.08f, eyeY + r * 0.42f),
                    r * 0.13f,
                    Color(0xFF64B5F6),
                    rimLight = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodScreen(
    vm: MoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    var selectedCode by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    // today 首次到达（或保存后回写）时同步选择，使「今日已打卡」可被编辑/覆盖。
    LaunchedEffect(today) {
        if (today != null) {
            selectedCode = today!!.code
            note = today!!.note
        }
    }

    val context = LocalContext.current
    val todayLabel = stringResource(R.string.mood_today)
    val noteHint = stringResource(R.string.mood_note_hint)
    val saveLabel = stringResource(R.string.mood_save)
    val savedToast = stringResource(R.string.mood_saved_toast)
    val statsLabel = stringResource(R.string.mood_month_stats)
    val noData = stringResource(R.string.mood_no_data)
    val recentLabel = stringResource(R.string.mood_recent)
    val recentEmpty = stringResource(R.string.mood_recent_empty)

    // 本月情绪分布
    val now = LocalDate.now()
    val monthKey = now.year * 100 + now.monthValue
    val thisMonth = entries.filter {
        val d = LocalDate.ofEpochDay(it.epochDay)
        d.year * 100 + d.monthValue == monthKey
    }
    val counts = MOODS.associate { m -> m.code to thisMonth.count { e -> e.code == m.code } }
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val recent = entries.takeLast(7).reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_mood_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SimHintCard(stringResource(R.string.tools_mood_hint))
            Text(todayLabel, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MOODS.forEach { m ->
                    FilterChip(
                        selected = selectedCode == m.code,
                        onClick = { selectedCode = m.code },
                        label = { Text(stringResource(m.labelRes)) },
                        leadingIcon = { MoodFace(m.code, 20.dp) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(noteHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val code = selectedCode ?: return@Button
                    vm.saveToday(code, note)
                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                },
                enabled = selectedCode != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(saveLabel) }

            Spacer(Modifier.height(Spacing.sm))
            Text(statsLabel, style = MaterialTheme.typography.titleSmall)
            if (thisMonth.isEmpty()) {
                Text(noData, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MOODS.forEach { m ->
                    val c = counts[m.code] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        MoodFace(m.code, 22.dp)
                        Text(
                            stringResource(m.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(48.dp),
                        )
                        LinearProgressIndicator(
                            progress = { c / maxCount.toFloat() },
                            modifier = Modifier.weight(1f),
                        )
                        Text("$c", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(recentLabel, style = MaterialTheme.typography.titleSmall)
            if (recent.isEmpty()) {
                Text(
                    recentEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            } else {
                recent.forEach { e ->
                    val d = LocalDate.ofEpochDay(e.epochDay)
                    val mood = MOODS.firstOrNull { it.code == e.code }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text("${d.monthValue}.${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
                            if (mood != null) {
                                MoodFace(mood.code, 24.dp)
                            }
                            val moodLabel = if (mood != null) stringResource(mood.labelRes) else "${e.code}"
                            Text(
                                moodLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (e.note.isNotBlank()) {
                                Text(
                                    e.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
