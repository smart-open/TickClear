package com.tickclear.app.ui.tools

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockOverlayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var running by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 从系统设置页返回后重新检测授权状态。
        canOverlay = Settings.canDrawOverlays(context)
    }

    val grantIntent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_clock_overlay_title)) },
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
            if (!canOverlay) {
                Text(
                    stringResource(R.string.clock_overlay_grant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { permLauncher.launch(grantIntent) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.clock_overlay_grant))
                }
                Text(
                    stringResource(R.string.clock_overlay_no_perm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = {
                        if (running) {
                            context.stopService(Intent(context, ClockOverlayService::class.java))
                            running = false
                        } else {
                            runCatching {
                                ContextCompat.startForegroundService(context, Intent(context, ClockOverlayService::class.java))
                                running = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (running) {
                            stringResource(R.string.clock_overlay_stop)
                        } else {
                            stringResource(R.string.clock_overlay_start)
                        },
                    )
                }
                if (running) {
                    Text(
                        stringResource(R.string.clock_overlay_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.clock_overlay_grant_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
