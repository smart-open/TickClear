package com.tickclear.app.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

private const val NOISE_CALIBRATION_DB = 90f // 麦克风相对灵敏度校准偏移（约值）

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
    var db by remember { mutableStateOf(0) }

    LaunchedEffect(measuring, permissionGranted) {
        if (!measuring || !permissionGranted) {
            db = 0
            return@LaunchedEffect
        }
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
                        }
                    }
                } finally {
                    runCatching { record.stop() }
                    runCatching { record.release() }
                }
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
        }
    }
}
