package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn

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
    var freq by remember { mutableStateOf(8) } // 频闪频率（Hz）

    // 控制手电筒：常亮或频闪。离开 Effect 时在 finally 中关灯。
    LaunchedEffect(on, mode, freq, permissionGranted, hasFlash) {
        if (!hasFlash || !permissionGranted || !on) {
            if (cameraId != null) runCatching { cameraManager.setTorchMode(cameraId, false) }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasFlash) {
                Text(
                    stringResource(R.string.flashlight_no_flash),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Scaffold
            }
            if (!permissionGranted) {
                Text(
                    stringResource(R.string.flashlight_permission_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.flashlight_grant))
                }
                return@Scaffold
            }

            Button(
                onClick = { on = !on },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (on) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (on) stringResource(R.string.flashlight_turn_off) else stringResource(R.string.flashlight_turn_on))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = mode == FlashMode.STEADY,
                    onClick = { mode = FlashMode.STEADY },
                    label = { Text(stringResource(R.string.flashlight_mode_steady)) },
                )
                FilterChip(
                    selected = mode == FlashMode.STROBE,
                    onClick = { mode = FlashMode.STROBE },
                    label = { Text(stringResource(R.string.flashlight_mode_strobe)) },
                )
            }

            if (mode == FlashMode.STROBE) {
                Text(stringResource(R.string.flashlight_strobe_freq))
                Text(
                    stringResource(R.string.flashlight_strobe_hz, freq),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = freq.toFloat(),
                    onValueChange = { freq = it.toInt() },
                    valueRange = 1f..20f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
