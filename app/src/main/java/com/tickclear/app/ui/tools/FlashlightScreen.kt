package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class FlashMode { STEADY, STROBE }

/** 返回带闪光灯的相机 id（优先后置），无闪光灯返回 null。 */
private fun findFlashCameraId(cm: CameraManager): String? = try {
    cm.cameraIdList.firstOrNull { id ->
        cm.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
} catch (_: Exception) {
    null
}

/**
 * 手电筒（V2.9++ 美化版）：
 * - 中央大圆按钮：开灯时填充主题色 + 边缘亮光晕；关灯时仅描边；
 * - 下方 Segmented 风格的模式切换（Material3 FilterChip group）；
 * - 频闪模式滑杆 + 实时 Hz 数值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember { findFlashCameraId(cameraManager) }
    val hasFlash = cameraId != null

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    var on by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(FlashMode.STEADY) }
    var freq by remember { mutableIntStateOf(8) } // 频闪频率（Hz）

    // 控制手电筒：常亮或频闪。离开 Effect 时在 finally 中关灯。
    LaunchedEffect(on, mode, freq, permissionGranted, hasFlash) {
        if (cameraId == null) return@LaunchedEffect
        if (!hasFlash || !permissionGranted || !on) {
            runCatching { cameraManager.setTorchMode(cameraId, false) }
            return@LaunchedEffect
        }
        if (mode == FlashMode.STEADY) {
            runCatching { cameraManager.setTorchMode(cameraId, true) }
            try {
                awaitCancellation()
            } finally {
                runCatching { cameraManager.setTorchMode(cameraId, false) }
            }
        } else {
            val half = (1000L / (freq * 2)).coerceAtLeast(20L)
            try {
                while (true) {
                    runCatching { cameraManager.setTorchMode(cameraId, true) }
                    delay(half)
                    runCatching { cameraManager.setTorchMode(cameraId, false) }
                    delay(half)
                }
            } finally {
                runCatching { cameraManager.setTorchMode(cameraId, false) }
            }
        }
    }

    // 离开页面保险：关灯
    DisposableEffect(Unit) {
        onDispose {
            if (cameraId != null) runCatching { cameraManager.setTorchMode(cameraId, false) }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_flashlight_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surface,
                            surfaceVariant.copy(alpha = 0.6f),
                        ),
                    ),
                ),
        ) {
            when {
                !hasFlash -> {
                    Text(
                        stringResource(R.string.flashlight_no_flash),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                !permissionGranted -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            stringResource(R.string.flashlight_permission_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.flashlight_grant))
                        }
                    }
                }
                else -> FlashlightBody(
                    on = on,
                    onToggle = { on = !on },
                    mode = mode,
                    onModeChange = { mode = it },
                    freq = freq,
                    onFreqChange = { freq = it.roundToInt() },
                    primaryColor = primaryColor,
                    onPrimary = onPrimary,
                    onSurfaceVariant = onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlashlightBody(
    on: Boolean,
    onToggle: () -> Unit,
    mode: FlashMode,
    onModeChange: (FlashMode) -> Unit,
    freq: Int,
    onFreqChange: (Float) -> Unit,
    primaryColor: androidx.compose.ui.graphics.Color,
    onPrimary: androidx.compose.ui.graphics.Color,
    onSurfaceVariant: androidx.compose.ui.graphics.Color,
) {
    // 灯开时按钮轻微放大，模拟按压呼吸感
    val scale by animateFloatAsState(
        targetValue = if (on) 1.05f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "flash-button-scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xl))

        // 中央大圆按钮：开灯时填充主题色 + 渐变光晕；关灯时描边
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                // 外圈装饰光晕
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(1.15f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )
            }
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(percent = 50),
                color = if (on) primaryColor else MaterialTheme.colorScheme.surface,
                tonalElevation = if (on) 8.dp else 2.dp,
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (on) Icons.Filled.Bolt else Icons.Filled.FlashlightOn,
                        contentDescription = null,
                        tint = if (on) onPrimary else primaryColor,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }

        Text(
            text = stringResource(if (on) R.string.flashlight_turn_off else R.string.flashlight_turn_on),
            style = MaterialTheme.typography.titleLarge,
            color = if (on) primaryColor else onSurfaceVariant,
        )

        // 模式切换：SaleSegmented 风格的双 Chip
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = mode == FlashMode.STEADY,
                onClick = { onModeChange(FlashMode.STEADY) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.FlashlightOn,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = { Text(stringResource(R.string.flashlight_mode_steady)) },
                shape = RoundedCornerShape(12.dp),
            )
            FilterChip(
                selected = mode == FlashMode.STROBE,
                onClick = { onModeChange(FlashMode.STROBE) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.FlashOff,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = { Text(stringResource(R.string.flashlight_mode_strobe)) },
                shape = RoundedCornerShape(12.dp),
            )
        }

        // 频闪频率滑杆
        if (mode == FlashMode.STROBE) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
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
                        Text(stringResource(R.string.flashlight_strobe_freq))
                        Text(
                            stringResource(R.string.flashlight_strobe_hz, freq),
                            color = primaryColor,
                        )
                    }
                    Slider(
                        value = freq.toFloat(),
                        onValueChange = onFreqChange,
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                        ),
                    )
                }
            }
        }
    }
}

// 局部 Card 简便别名，避免在 Body 里多个 import
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.CardColors = androidx.compose.material3.CardDefaults.cardColors(),
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = colors,
        shape = RoundedCornerShape(16.dp),
    ) { content() }
}
