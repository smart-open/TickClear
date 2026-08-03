package com.tickclear.app.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.RecycleBinItem
import com.tickclear.app.ui.components.EmptyStateGuide
import com.tickclear.app.ui.components.showTimedSnackbar
import com.tickclear.app.ui.stats.CardColors
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
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedKeys by viewModel.selectedKeys.collectAsStateWithLifecycle()
    val selectedCount by viewModel.selectedCount.collectAsStateWithLifecycle()
    val lastRestored by viewModel.lastRestored.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var purgeAllConfirm by rememberSaveable { mutableStateOf(false) }
    var itemToPurgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var batchPurgeConfirm by rememberSaveable { mutableStateOf(false) }
    // 旋转后保持「彻底删除」确认弹层（存 id，按 id 从列表恢复对象）。
    val itemToPurge = items.find { it.id == itemToPurgeId }

    // 单条恢复后撤销提示：恢复即弹出 snackbar，点「撤销」重新软删。
    LaunchedEffect(lastRestored) {
        val item = lastRestored ?: return@LaunchedEffect
        val name = item.name.ifEmpty { context.getString(R.string.recycle_bin_unnamed) }
        val result = snackbarHostState.showTimedSnackbar(
            message = context.getString(R.string.recycle_bin_restore_snack, name),
            actionLabel = context.getString(R.string.recycle_bin_undo_hint),
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoRestore()
            SnackbarResult.Dismissed -> viewModel.clearLastRestored()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    if (selectionMode) {
                        Text(stringResource(R.string.recycle_bin_selection_count, selectedCount))
                    } else {
                        Text(stringResource(R.string.recycle_bin_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) viewModel.clearSelection() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (items.isEmpty()) return@TopAppBar
                    if (selectionMode) {
                        TextButton(onClick = { viewModel.selectAll(items) }) {
                            Text(stringResource(R.string.recycle_bin_select_all))
                        }
                        TextButton(
                            enabled = selectedCount > 0,
                            onClick = { viewModel.restoreSelected(items) },
                        ) {
                            Text(stringResource(R.string.recycle_bin_batch_restore))
                        }
                        TextButton(
                            enabled = selectedCount > 0,
                            onClick = { batchPurgeConfirm = true },
                        ) {
                            Text(stringResource(R.string.recycle_bin_batch_purge))
                        }
                    } else {
                        TextButton(onClick = { viewModel.selectAll(items) }) {
                            Text(stringResource(R.string.recycle_bin_select))
                        }
                        // MaterialTheme.colorScheme 是 @Composable 属性，必须在组合上下文取值后再交给 onClick。
                        val cardColors = CardColors(
                            bg = MaterialTheme.colorScheme.background.toArgb(),
                            surface = MaterialTheme.colorScheme.surface.toArgb(),
                            primary = MaterialTheme.colorScheme.primary.toArgb(),
                            onSurface = MaterialTheme.colorScheme.onSurface.toArgb(),
                            onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                        )
                        TextButton(onClick = {
                            val bmp = RecycleBinExportCard.generate(context, items, cardColors)
                            RecycleBinExportCard.share(context, bmp)
                        }) {
                            Text(stringResource(R.string.recycle_bin_export))
                        }
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
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).padding(innerPadding)) {
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
                        Box(modifier = Modifier.fillMaxWidth().animateItem()) {
                            RecycleBinRow(
                                item = item,
                                selectionMode = selectionMode,
                                isSelected = "${item.type}:${item.id}" in selectedKeys,
                                onToggleSelect = { viewModel.toggleSelection(item) },
                                onLongClick = { viewModel.toggleSelection(item) },
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

    if (batchPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { batchPurgeConfirm = false },
            title = { Text(stringResource(R.string.recycle_bin_batch_purge)) },
            text = { Text(stringResource(R.string.recycle_bin_batch_purge_confirm, selectedCount)) },
            confirmButton = {
                TextButton(onClick = { viewModel.purgeSelected(items); batchPurgeConfirm = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { batchPurgeConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinRow(
    item: RecycleBinItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val dateStr = remember(item.deletedAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.deletedAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.clickable(onClick = onToggleSelect)
                } else {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
        }
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
        if (!selectionMode) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.RestoreFromTrash, contentDescription = stringResource(R.string.recycle_bin_restore))
            }
            IconButton(onClick = onPurge) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recycle_bin_purge_now))
            }
        }
    }
}
