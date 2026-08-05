package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.NoiseSynth
import com.tickclear.app.ui.theme.Spacing

/**
 * 睡眠白噪音合集（雨声 / 咖啡馆 / 溪流）：本地程序化循环音效，无需联网。
 * 支持场景切换、播放/停止与音量调节；离开页面自动停止。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteNoiseScreen(onBack: () -> Unit) {
    val scenes = listOf(
        "rain" to R.string.white_noise_rain,
        "cafe" to R.string.white_noise_cafe,
        "stream" to R.string.white_noise_stream,
    )
    var selected by remember { mutableStateOf("rain") }
    var playing by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.7f) }

    fun ensurePlaying() {
        NoiseSynth.play(selected, volume)
        playing = true
    }

    fun stop() {
        NoiseSynth.stop()
        playing = false
    }

    DisposableEffect(Unit) {
        onDispose { NoiseSynth.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_white_noise_title)) },
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
            SimHintCard(stringResource(R.string.tools_white_noise_hint))

            Text(
                stringResource(R.string.white_noise_volume),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                scenes.forEach { (key, labelRes) ->
                    FilterChip(
                        selected = selected == key,
                        onClick = {
                            selected = key
                            if (playing) NoiseSynth.play(key, volume)
                        },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(
                onClick = { if (playing) stop() else ensurePlaying() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                Text(if (playing) stringResource(R.string.white_noise_stop) else stringResource(R.string.white_noise_play))
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.white_noise_volume))
                        Text(
                            stringResource(R.string.white_noise_volume),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            if (playing) NoiseSynth.setVolume(it)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}
