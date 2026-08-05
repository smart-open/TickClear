package com.tickclear.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.AnimalSynth
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class Animal(val key: String, val nameRes: Int, val emoji: String)

private val ANIMALS = listOf(
    Animal("dog", R.string.tools_animal_dog, "🐶"),
    Animal("cat", R.string.tools_animal_cat, "🐱"),
    Animal("cow", R.string.tools_animal_cow, "🐮"),
    Animal("sheep", R.string.tools_animal_sheep, "🐑"),
    Animal("duck", R.string.tools_animal_duck, "🦆"),
    Animal("pig", R.string.tools_animal_pig, "🐷"),
    Animal("chicken", R.string.tools_animal_chicken, "🐔"),
    Animal("lion", R.string.tools_animal_lion, "🦁"),
    Animal("tiger", R.string.tools_animal_tiger, "🐯"),
    Animal("bird", R.string.tools_animal_bird, "🐦"),
    Animal("frog", R.string.tools_animal_frog, "🐸"),
    Animal("horse", R.string.tools_animal_horse, "🐴"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalSoundScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 兜底异常处理器：哪怕合成/播放出错也只吞掉，绝不让后台协程异常冲垮进程。
    val scope = rememberCoroutineScope {
        CoroutineExceptionHandler { _, _ -> /* 静默 */ }
    }

    DisposableEffect(Unit) {
        onDispose { AnimalSynth.stop() }
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(ANIMALS, key = { it.key }) { animal ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.Default) {
                                    AnimalSynth.play(animal.key)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(animal.emoji, fontSize = 40.sp)
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
