package com.tickclear.app.ui.tools

import android.content.Context
import androidx.compose.foundation.background
import com.tickclear.app.ui.components.LockScreenOrientation
import com.tickclear.app.ui.components.OrientationLockState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tickclear.app.R
import com.tickclear.app.domain.tools.PianoSynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.pow

/**
 * 电子琴（V2.9++ 模拟解压）。
 *
 * 竖屏展示 1–7 简单音符（一个八度，白键标 1–7 / 哆来咪…），横屏扩展为两个八度更好弹。
 * 琴键为拟真黑白键布局，点按即响、按住可延长；右上角按钮切换横竖屏。
 * 另提供「小星星 / 两只老虎」经典儿歌预设，一键自动弹奏，方便直接弹曲子。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope {
        CoroutineExceptionHandler { _, _ -> /* 合成/播放异常静默吞掉，绝不冲垮进程 */ }
    }
    // 横竖屏偏好用 saveable 持久化：旋转重建后能保留用户选择（与 RulerScreen 同款范式）。
    // 横竖屏偏好存进程级单例 OrientationLockState：部分 ROM（如 HyperOS）旋转会整页重建，
    // rememberSaveable 不可靠恢复，改用 remember 从单例恢复，保证用户选择不丢、不被弹回竖屏。
    var landscape by remember { mutableStateOf(OrientationLockState.desiredLandscape) }
    // 用显式 MutableState 持有按下的键集合，便于在「容器层 pointerInput」闭包中稳定读写（不受重组影响）。
    val pressedState = remember { mutableStateOf(emptySet<Int>()) }
    var playingSong by remember { mutableStateOf(false) }
    // 跟踪自动演奏协程，便于「停止」按钮中途取消；取消时 runSong 的 finally 会释放仍按住的音，避免卡音。
    var songJob by remember { mutableStateOf<Job?>(null) }

    fun pressNote(midi: Int) {
        pressedState.value = pressedState.value + midi
        PianoSynth.noteOn(midiToFreq(midi))
    }

    fun releaseNote(midi: Int) {
        pressedState.value = pressedState.value - midi
        PianoSynth.noteOff(midiToFreq(midi))
    }

    DisposableEffect(Unit) {
        onDispose { PianoSynth.stop() }
    }

    // 方向锁定抽成共享组件（带旋转后自愈兜底，见 ScreenOrientationLocker）。
    LockScreenOrientation(landscape)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_piano_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { landscape = !landscape }) {
                        Icon(
                            Icons.Filled.ScreenRotation,
                            contentDescription = stringResource(R.string.piano_rotate),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.md),
        ) {
            Text(
                stringResource(R.string.tools_piano_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm),
            )

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val startMidi = 60 // C4
                val endMidi = if (landscape) 83 else 71 // 71=B4(1 八度) / 83=B5(2 八度)
                val keys = (startMidi..endMidi).map { m ->
                    val pc = m % 12
                    val black = pc in BLACK_PC
                    PianoKey(
                        midi = m,
                        isBlack = black,
                        num = if (black) null else NUM_BY_PC[pc],
                        solRes = if (black) null else SOL_BY_PC[pc],
                    )
                }
                val whites = keys.filter { !it.isBlack }
                val blacks = keys.filter { it.isBlack }
                val whiteIdx = whites.mapIndexed { i, k -> k.midi to i }.toMap()
                val wkW = maxWidth / whites.size
                val bkW = wkW * 0.62f
                val keyH = maxHeight * 0.82f

                // 统一在容器层做触摸命中：把指针坐标映射到具体琴键（白/黑），
                // 彻底规避「分层命中 / 偏移」导致点错键或无响应的问题（支持多指和音与滑奏）。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(keyH)
                        .pointerInput(whites.size, blacks.size) {
                            val wkWpx = size.width.toFloat() / whites.size
                            val bkWpx = wkWpx * 0.62f
                            val blackTop = size.height * 0.62f
                            val blackCx = blacks.map { bk ->
                                (whiteIdx[bk.midi - 1]!! + 1) * wkWpx
                            }
                            fun hitTest(px: Float, py: Float): Int {
                                val wi = (px / wkWpx).toInt().coerceIn(0, whites.lastIndex)
                                for (i in blacks.indices) {
                                    val cx = blackCx[i]
                                    if (px >= cx - bkWpx / 2f && px <= cx + bkWpx / 2f && py <= blackTop) {
                                        return blacks[i].midi
                                    }
                                }
                                return whites[wi].midi
                            }
                            val active = mutableMapOf<Int, Int>() // pointerId -> midi
                            awaitPointerEventScope {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val present = mutableSetOf<Int>()
                                    for (ch in ev.changes) {
                                        val id = ch.id.value.toInt()
                                        present.add(id)
                                        val midi = hitTest(ch.position.x, ch.position.y)
                                        when {
                                            ch.changedToDownIgnoreConsumed() -> {
                                                active[id] = midi
                                                pressNote(midi)
                                                Haptic.vibrate(context, 12)
                                            }
                                            ch.changedToUpIgnoreConsumed() -> {
                                                active.remove(id)?.let { releaseNote(it) }
                                            }
                                            ch.pressed -> {
                                                val cur = active[id]
                                                if (cur != null && cur != midi) {
                                                    releaseNote(cur)
                                                    active[id] = midi
                                                    pressNote(midi)
                                                }
                                            }
                                        }
                                    }
                                    // 指针被系统取消（未收到 up 事件）时回收，避免卡音。
                                    for (id in active.keys.toSet()) {
                                        if (id !in present) active.remove(id)?.let { releaseNote(it) }
                                    }
                                }
                            }
                        },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        whites.forEach { wk ->
                            WhiteKey(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                key = wk,
                                isPressed = pressedState.value.contains(wk.midi),
                            )
                        }
                    }
                    blacks.forEach { bk ->
                        val wi = whiteIdx[bk.midi - 1] ?: return@forEach
                        val x = (wi.toFloat() + 1f) * wkW.value - bkW.value / 2f
                        Box(
                            Modifier
                                .offset(x = x.dp, y = 0.dp)
                                .width(bkW)
                                .height(keyH * 0.62f)
                                .zIndex(1f),
                        ) {
                            BlackKey(
                                key = bk,
                                isPressed = pressedState.value.contains(bk.midi),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(
                    onClick = {
                        if (playingSong) return@OutlinedButton
                        playingSong = true
                        songJob = scope.launch {
                            try { runSong(SONG_TWINKLE, ::pressNote, ::releaseNote) }
                            finally { playingSong = false; songJob = null }
                        }
                    },
                    enabled = !playingSong,
                ) { Text(stringResource(R.string.piano_song_twinkle)) }
                OutlinedButton(
                    onClick = {
                        if (playingSong) return@OutlinedButton
                        playingSong = true
                        songJob = scope.launch {
                            try { runSong(SONG_TIGER, ::pressNote, ::releaseNote) }
                            finally { playingSong = false; songJob = null }
                        }
                    },
                    enabled = !playingSong,
                ) { Text(stringResource(R.string.piano_song_tiger)) }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                // 停止按钮：仅在自动演奏进行中可点击；点击取消协程，runSong 的 finally 释放仍按住的音。
                OutlinedButton(
                    onClick = { songJob?.cancel() },
                    enabled = playingSong,
                ) { Text(stringResource(R.string.piano_song_stop)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun WhiteKey(
    modifier: Modifier,
    key: PianoKey,
    isPressed: Boolean,
) {
    Box(
        modifier
            .background(
                color = if (isPressed) MaterialTheme.colorScheme.primary else Color(0xFFFCFCFD),
                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
            )
            .border(
                1.dp,
                Color(0xFFD0D5DD),
                RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (key.num != null) {
                Text(
                    key.num,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPressed) MaterialTheme.colorScheme.onPrimary else Color(0xFF374151),
                )
                if (key.solRes != null) {
                    Text(
                        stringResource(key.solRes),
                        fontSize = 11.sp,
                        color = if (isPressed) MaterialTheme.colorScheme.onPrimary else Color(0xFF6B7280),
                    )
                }
            }
            Text(
                noteName(key.midi),
                fontSize = 9.sp,
                color = Color(0xFF9CA3AF),
            )
        }
    }
}

@Composable
private fun BlackKey(
    key: PianoKey,
    isPressed: Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                color = if (isPressed) MaterialTheme.colorScheme.primary else Color(0xFF2B2F36),
                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp),
            )
            .border(
                1.dp,
                Color(0xFF111418),
                RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            noteName(key.midi),
            fontSize = 9.sp,
            color = if (isPressed) MaterialTheme.colorScheme.onPrimary else Color(0xFF9CA3AF),
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

// ---------- 数据 / 工具 ----------

private data class PianoKey(
    val midi: Int,
    val isBlack: Boolean,
    val num: String?,
    val solRes: Int?,
)

private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

private fun noteName(midi: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[midi % 12]}${midi / 12 - 1}"
}

private val BLACK_PC = setOf(1, 3, 6, 8, 10)
private val NUM_BY_PC = mapOf(
    0 to "1", 2 to "2", 4 to "3", 5 to "4", 7 to "5", 9 to "6", 11 to "7",
)
private val SOL_BY_PC = mapOf(
    0 to R.string.piano_sol_do, 2 to R.string.piano_sol_re, 4 to R.string.piano_sol_mi,
    5 to R.string.piano_sol_fa, 7 to R.string.piano_sol_sol, 9 to R.string.piano_sol_la,
    11 to R.string.piano_sol_ti,
)

/** 自动弹奏一段旋律：midi 音符 + 时值(ms)。取消时释放仍按住的音，避免卡音。 */
private suspend fun runSong(
    seq: List<Pair<Int, Long>>,
    press: (Int) -> Unit,
    release: (Int) -> Unit,
) {
    var heldMidi: Int? = null
    try {
        for ((midi, ms) in seq) {
            heldMidi = midi
            press(midi)
            delay(ms)
            release(midi)
            heldMidi = null
            delay(70)
        }
    } finally {
        heldMidi?.let { release(it) }
    }
}

// 小星星（Twinkle Twinkle Little Star，C 大调，1=C4=60）。经典儿歌主旋律，
// 每乐句收尾音拉长到 800ms，其余 400ms，听感更分明。
private val SONG_TWINKLE = listOf(
    60 to 400L, 60 to 400L, 67 to 400L, 67 to 400L, 69 to 400L, 69 to 400L, 67 to 800L,
    65 to 400L, 65 to 400L, 64 to 400L, 64 to 400L, 62 to 400L, 62 to 400L, 60 to 800L,
    67 to 400L, 67 to 400L, 65 to 400L, 65 to 400L, 64 to 400L, 64 to 400L, 62 to 800L,
    67 to 400L, 67 to 400L, 65 to 400L, 65 to 400L, 64 to 400L, 64 to 400L, 62 to 800L,
    60 to 400L, 60 to 400L, 67 to 400L, 67 to 400L, 69 to 400L, 69 to 400L, 67 to 800L,
    65 to 400L, 65 to 400L, 64 to 400L, 64 to 400L, 62 to 400L, 62 to 400L, 60 to 800L,
)

// 两只老虎（Two Tigers / Frère Jacques，C 大调，1=C4=60）。经典儿歌主旋律，
// “跑得快 / 真奇怪”乐句收尾音拉长到 800ms。
private val SONG_TIGER = listOf(
    60 to 400L, 62 to 400L, 64 to 400L, 60 to 400L,
    60 to 400L, 62 to 400L, 64 to 400L, 60 to 400L,
    64 to 400L, 65 to 400L, 67 to 800L,
    64 to 400L, 65 to 400L, 67 to 800L,
    67 to 400L, 69 to 400L, 67 to 400L, 65 to 400L, 64 to 400L, 60 to 400L,
    67 to 400L, 69 to 400L, 67 to 400L, 65 to 400L, 64 to 400L, 60 to 400L,
    60 to 400L, 67 to 400L, 60 to 800L,
    60 to 400L, 67 to 400L, 60 to 800L,
)

