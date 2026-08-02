package com.tickclear.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.BuildConfig
import com.tickclear.app.R
import com.tickclear.app.ui.theme.ThemeMode
import com.tickclear.app.ui.theme.ThemeSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeSkin by viewModel.themeSkin.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.about_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        // 版本号取自 BuildConfig，随 build.gradle.kts 的 versionName/versionCode 自动同步；
                        // 曾经写死为「版本 1.0.0 (Debug)」，正式包也照此显示，属用户可见错误信息。
                        stringResource(
                            if (BuildConfig.DEBUG) R.string.about_version_label_debug else R.string.about_version_label,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_storage_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_storage_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_assistant_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_assistant_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(
                    R.string.about_current_theme,
                    stringResource(
                        when (themeMode) {
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                            ThemeMode.DYNAMIC -> R.string.settings_theme_dynamic_short
                        },
                    ),
                    stringResource(
                        when (themeSkin) {
                            ThemeSkin.BLUE -> R.string.settings_skin_blue
                            ThemeSkin.GREEN -> R.string.settings_skin_green
                            ThemeSkin.PURPLE -> R.string.settings_skin_purple
                            ThemeSkin.ORANGE -> R.string.settings_skin_orange
                            ThemeSkin.ROSE -> R.string.settings_skin_rose
                            ThemeSkin.TEAL -> R.string.settings_skin_teal
                        },
                    ),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
