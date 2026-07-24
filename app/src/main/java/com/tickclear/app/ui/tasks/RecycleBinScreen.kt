package com.tickclear.app.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.ui.components.EmptyStateGuide
import com.tickclear.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    viewModel: RecycleBinViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToToday: () -> Unit = {},
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var purgeAllConfirm by rememberSaveable { mutableStateOf(false) }
    var itemToPurgeId by rememberSaveable { mutableStateOf<String?>(null) }
    // 旋转后保持「彻底删除」确认弹层（存 id，按 id 从列表恢复对象）。
    val itemToPurge = items.find { it.id == itemToPurgeId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recycle_bin_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { purgeAllConfirm = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.recycle_bin_purge_all))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            EmptyStateGuide(
                icon = "🗑️",
                title = stringResource(R.string.recycle_bin_empty),
                message = stringResource(R.string.recycle_bin_empty_desc),
                actionLabel = stringResource(R.string.recycle_bin_go_today),
                onAction = onNavigateToToday,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Text(
                    stringResource(R.string.recycle_bin_auto_purge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { "${it.type}_${it.id}" }) { item ->
                        // V2.21 列表项进入/重排动画
                        Box(modifier = Modifier.fillMaxWidth().animateItemPlacement()) {
                            RecycleBinRow(
                                item = item,
                                onRestore = { viewModel.restore(item) },
                                onPurge = { itemToPurgeId = item.id },
                            )
                        }
                    }
                }
            }
        }
    }

    if (purgeAllConfirm) {
        AlertDialog(
            onDismissRequest = { purgeAllConfirm = false },
            title = { Text(stringResource(R.string.recycle_bin_purge_all)) },
            text = { Text(stringResource(R.string.recycle_bin_purge_all_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.purgeAll(); purgeAllConfirm = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { purgeAllConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    itemToPurge?.let { it ->
        AlertDialog(
            onDismissRequest = { itemToPurgeId = null },
            title = { Text(stringResource(R.string.recycle_bin_purge_now)) },
            text = { Text(stringResource(R.string.recycle_bin_purge_confirm, 1)) },
            confirmButton = {
                TextButton(onClick = { viewModel.purge(it); itemToPurgeId = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToPurgeId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RecycleBinRow(
    item: RecycleBinItem,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val dateStr = remember(item.deletedAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.deletedAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name.ifEmpty { stringResource(R.string.recycle_bin_unnamed) }, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (item.type == "task") R.string.recycle_bin_type_task else R.string.recycle_bin_type_group,
                    dateStr,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Filled.RestoreFromTrash, contentDescription = stringResource(R.string.recycle_bin_restore))
        }
        IconButton(onClick = onPurge) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recycle_bin_purge_now))
        }
    }
}
