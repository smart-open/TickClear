package com.tickclear.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.random.Random

/** 出拳：0=石头 1=剪刀 2=布。 */
private val CHOICE_LABELS = listOf(R.string.rps_rock, R.string.rps_scissors, R.string.rps_paper)

/**
 * 石头剪刀布（人机对战，休闲小游戏）。
 * 点一下出拳，机器随机应对，带战绩统计与触觉反馈。纯本地，无联网。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<Int?>(null) }
    var machine by remember { mutableStateOf<Int?>(null) }
    var resultWon by remember { mutableStateOf<Boolean?>(null) }
    var wins by remember { mutableIntStateOf(0) }
    var losses by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    fun play(choice: Int) {
        val m = Random.nextInt(3)
        player = choice
        machine = m
        resultWon = when {
            choice == m -> {
                draws++
                null
            }
            (choice == 0 && m == 1) || (choice == 1 && m == 2) || (choice == 2 && m == 0) -> {
                wins++
                true
            }
            else -> {
                losses++
                false
            }
        }
        Haptic.vibrate(context, 50)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_rps_title)) },
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
            SimHintCard(stringResource(R.string.tools_rps_hint))

            // 战绩
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ScoreCard(
                    label = stringResource(R.string.rps_wins),
                    value = wins,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ScoreCard(
                    label = stringResource(R.string.rps_losses),
                    value = losses,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                ScoreCard(
                    label = stringResource(R.string.rps_draws),
                    value = draws,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            // 对战展示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ChoiceBadge(stringResource(R.string.rps_you), player)
                        ChoiceBadge(stringResource(R.string.rps_machine), machine)
                    }
                    if (player != null && machine != null) {
                        val resultText = when (resultWon) {
                            true -> stringResource(R.string.rps_win)
                            false -> stringResource(R.string.rps_lose)
                            null -> stringResource(R.string.rps_draw)
                        }
                        Text(
                            stringResource(
                                R.string.rps_result_format,
                                stringResource(CHOICE_LABELS[player!!]),
                                stringResource(CHOICE_LABELS[machine!!]),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            resultText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (resultWon) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            // 出拳按钮
            Text(
                stringResource(R.string.tools_rps_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CHOICE_LABELS.forEachIndexed { idx, labelRes ->
                    OutlinedButton(
                        onClick = { play(idx) },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    wins = 0; losses = 0; draws = 0
                    player = null; machine = null; resultWon = null
                },
            ) { Text(stringResource(R.string.rps_reset)) }
        }
    }
}

@Composable
private fun ScoreCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChoiceBadge(who: String, choice: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                choice?.let { stringResource(CHOICE_LABELS[it]) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(who, style = MaterialTheme.typography.labelMedium)
    }
}
