package com.tickclear.app.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.tickclear.app.R
import com.tickclear.app.domain.tools.NoiseAssessment
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import java.util.Locale

private const val NOISE_CALIBRATION_DB = 90f // 麦克风相对灵敏度校准偏移（约值）

/** 少于该时长的测量样本不足以计算等效声级，不出评价报告。 */
private const val NOISE_MIN_SECONDS = 3

/** 一次测量结束后的评价数据。 */
private data class NoiseReport(
    val leqDb: Double,
    val peakDb: Int,
    val durationSec: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseMeterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    var measuring by remember { mutableStateOf(false) }
    var db by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<NoiseReport?>(null) }
    var tooShort by remember { mutableStateOf(false) }

    LaunchedEffect(measuring, permissionGranted) {
        if (!measuring || !permissionGranted) {
            return@LaunchedEffect
        }
        db = 0
        report = null
        tooShort = false

        // 声级必须按能量累加（Σ10^(L/10)）后再取平均还原 Leq，算术平均会严重低估瞬时强噪声
        var energySum = 0.0
        var sampleCount = 0L
        var peakDb = 0.0
        val startedAt = System.currentTimeMillis()
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val sampleRate = 44100
                    val minBuf = AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                    val bufSize = if (minBuf > 0) minBuf else 2048
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufSize,
                    )
                    record.startRecording()
                    val buf = ShortArray(1024)
                    try {
                        while (isActive) {
                            val n = record.read(buf, 0, buf.size)
                            if (n > 0) {
                                var sumSq = 0.0
                                for (i in 0 until n) {
                                    val v = buf[i].toDouble()
                                    sumSq += v * v
                                }
                                val rms = sqrt(sumSq / n)
                                val dbFs = if (rms > 0) 20 * log10(rms / 32767.0) else -100.0
                                val spl = (dbFs + NOISE_CALIBRATION_DB).coerceIn(0.0, 120.0)
                                db = spl.toInt()
                                energySum += NoiseAssessment.toEnergy(spl)
                                sampleCount++
                                if (spl > peakDb) peakDb = spl
                            }
                        }
                    } finally {
                        runCatching { record.stop() }
                        runCatching { record.release() }
                    }
                }
            }
        } finally {
            // 协程取消（用户点「停止测量」）也会走到这里，此处只做同步赋值不调挂起函数
            val durationSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
            if (sampleCount > 0 && durationSec >= NOISE_MIN_SECONDS) {
                report = NoiseReport(
                    leqDb = NoiseAssessment.fromEnergyMean(energySum / sampleCount),
                    peakDb = peakDb.roundToInt(),
                    durationSec = durationSec,
                )
            } else if (sampleCount > 0) {
                tooShort = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_noise_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!permissionGranted) {
                Text(
                    stringResource(R.string.noise_permission_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.noise_grant))
                }
                return@Scaffold
            }

            Text(
                stringResource(R.string.noise_db, db),
                style = MaterialTheme.typography.headlineLarge,
            )
            val levelRes = when {
                db < 40 -> R.string.noise_level_quiet
                db < 70 -> R.string.noise_level_moderate
                db < 90 -> R.string.noise_level_loud
                else -> R.string.noise_level_danger
            }
            Text(
                stringResource(levelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (db / 120f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))

            Button(
                onClick = { measuring = !measuring },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (measuring) stringResource(R.string.noise_stop) else stringResource(R.string.noise_start))
            }
            Text(
                stringResource(R.string.noise_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!measuring) {
                report?.let { NoiseReportCard(it) }
                if (tooShort) {
                    Text(
                        stringResource(R.string.noise_report_too_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 测量结束后的国标评价报告卡片。 */
@Composable
private fun NoiseReportCard(report: NoiseReport) {
    val grade = NoiseAssessment.gradeOf(report.leqDb)
    val gradeColor = when (grade) {
        NoiseAssessment.Grade.HARMFUL, NoiseAssessment.Grade.DANGEROUS -> MaterialTheme.colorScheme.error
        NoiseAssessment.Grade.POOR -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(R.string.noise_report_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.noise_report_basis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(
                    R.string.noise_report_leq,
                    String.format(Locale.getDefault(), "%.1f", report.leqDb),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.noise_report_peak, report.peakDb),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.noise_report_duration, report.durationSec),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.noise_report_grade, stringResource(gradeLabelRes(grade))),
                style = MaterialTheme.typography.titleSmall,
                color = gradeColor,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            Text(
                stringResource(R.string.noise_report_effect_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(gradeEffectRes(grade)),
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            Text(
                stringResource(R.string.noise_report_zone_title),
                style = MaterialTheme.typography.labelLarge,
            )
            val dayZone = NoiseAssessment.strictestDayZone(report.leqDb)
            Text(
                if (dayZone != null) {
                    stringResource(
                        R.string.noise_zone_day_pass,
                        stringResource(zoneLabelRes(dayZone)),
                        dayZone.dayLimit,
                    )
                } else {
                    stringResource(R.string.noise_zone_day_fail)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val nightZone = NoiseAssessment.strictestNightZone(report.leqDb)
            Text(
                if (nightZone != null) {
                    stringResource(
                        R.string.noise_zone_night_pass,
                        stringResource(zoneLabelRes(nightZone)),
                        nightZone.nightLimit,
                    )
                } else {
                    stringResource(R.string.noise_zone_night_fail)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            Text(
                stringResource(R.string.noise_report_exposure_title),
                style = MaterialTheme.typography.labelLarge,
            )
            val hours = NoiseAssessment.allowedExposureHours(report.leqDb)
            Text(
                when {
                    hours == null -> stringResource(R.string.noise_exposure_free)
                    hours <= 0.0 -> stringResource(R.string.noise_exposure_forbidden)
                    else -> stringResource(
                        R.string.noise_exposure_hours,
                        String.format(Locale.getDefault(), "%.1f", hours),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.noise_report_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun gradeLabelRes(grade: NoiseAssessment.Grade): Int = when (grade) {
    NoiseAssessment.Grade.EXCELLENT -> R.string.noise_grade_excellent
    NoiseAssessment.Grade.GOOD -> R.string.noise_grade_good
    NoiseAssessment.Grade.FAIR -> R.string.noise_grade_fair
    NoiseAssessment.Grade.POOR -> R.string.noise_grade_poor
    NoiseAssessment.Grade.HARMFUL -> R.string.noise_grade_harmful
    NoiseAssessment.Grade.DANGEROUS -> R.string.noise_grade_danger
}

@StringRes
private fun gradeEffectRes(grade: NoiseAssessment.Grade): Int = when (grade) {
    NoiseAssessment.Grade.EXCELLENT -> R.string.noise_effect_excellent
    NoiseAssessment.Grade.GOOD -> R.string.noise_effect_good
    NoiseAssessment.Grade.FAIR -> R.string.noise_effect_fair
    NoiseAssessment.Grade.POOR -> R.string.noise_effect_poor
    NoiseAssessment.Grade.HARMFUL -> R.string.noise_effect_harmful
    NoiseAssessment.Grade.DANGEROUS -> R.string.noise_effect_danger
}

@StringRes
private fun zoneLabelRes(zone: NoiseAssessment.FunctionZone): Int = when (zone) {
    NoiseAssessment.FunctionZone.ZONE_0 -> R.string.noise_zone_0
    NoiseAssessment.FunctionZone.ZONE_1 -> R.string.noise_zone_1
    NoiseAssessment.FunctionZone.ZONE_2 -> R.string.noise_zone_2
    NoiseAssessment.FunctionZone.ZONE_3 -> R.string.noise_zone_3
    NoiseAssessment.FunctionZone.ZONE_4A -> R.string.noise_zone_4a
}
