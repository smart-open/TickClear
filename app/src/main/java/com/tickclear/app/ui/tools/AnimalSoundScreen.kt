package com.tickclear.app.ui.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.AnimalSynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [hue] 为每种动物的专属色相，用于卡片光晕与播放高亮，避免 12 张卡片千篇一律。 */
private data class Animal(val key: String, val nameRes: Int, val emoji: String, val hue: Float)

private val ANIMALS = listOf(
    Animal("dog", R.string.tools_animal_dog, "🐶", 32f),
    Animal("cat", R.string.tools_animal_cat, "🐱", 18f),
    Animal("cow", R.string.tools_animal_cow, "🐮", 268f),
    Animal("sheep", R.string.tools_animal_sheep, "🐑", 210f),
    Animal("duck", R.string.tools_animal_duck, "🦆", 48f),
    Animal("pig", R.string.tools_animal_pig, "🐷", 332f),
    Animal("chicken", R.string.tools_animal_chicken, "🐔", 8f),
    Animal("lion", R.string.tools_animal_lion, "🦁", 40f),
    Animal("tiger", R.string.tools_animal_tiger, "🐯", 24f),
    Animal("bird", R.string.tools_animal_bird, "🐦", 195f),
    Animal("frog", R.string.tools_animal_frog, "🐸", 110f),
    Animal("horse", R.string.tools_animal_horse, "🐴", 300f),
)

/**
 * 动物叫声（V2.9++ 生活助手）。
 *
 * V2.9++ 可用性升级：原本点了只出声、屏幕毫无反馈——现在补上按压弹性缩放、
 * 播放中描边高亮、每种动物专属色相光晕与轻触感，让"点了没点上"一目了然。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalSoundScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 兜底异常处理器：哪怕合成/播放出错也只吞掉，绝不让后台协程异常冲垮进程。
    val scope = rememberCoroutineScope {
        CoroutineExceptionHandler { _, _ -> /* 静默 */ }
    }
    var playingKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { AnimalSynth.stop() }
    }

    // 播放高亮自动回落，避免高亮永久残留
    LaunchedEffect(playingKey) {
        if (playingKey != null) {
            delay(900)
            playingKey = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_animal_title)) },
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
        ) {
            Text(
                stringResource(R.string.tools_animal_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(ANIMALS, key = { it.key }) { animal ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val isPlaying = playingKey == animal.key
                    val tint = simColor(animal.hue, 1f)
                    val scale by animateFloatAsState(
                        targetValue = when {
                            pressed -> 0.92f
                            isPlaying -> 1.05f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
                        label = "animalCardScale",
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .clickable(
                                interactionSource = interaction,
                                indication = LocalIndication.current,
                            ) {
                                playingKey = animal.key
                                Haptic.vibrate(context, 20)
                                // 有真实录音优先用 MediaPlayer 播录音，否则回退合成
                                if (AnimalSynth.hasRecording(animal.key)) {
                                    AnimalSynth.playRaw(context, animal.key)
                                } else {
                                    scope.launch(Dispatchers.Default) {
                                        AnimalSynth.playSynth(animal.key)
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = if (isPlaying) BorderStroke(2.dp, tint) else null,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                tint.copy(alpha = if (isPlaying) 0.55f else 0.34f),
                                                tint.copy(alpha = 0.06f),
                                            ),
                                        ),
                                        shape = CircleShape,
                                    )
                                .border(
                                    BorderStroke(1.5.dp, tint.copy(alpha = if (isPlaying) 0.9f else 0.35f)),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(animal.emoji, fontSize = 34.sp)
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                stringResource(animal.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
