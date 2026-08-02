package com.tickclear.app.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.VaultEntry
import com.tickclear.app.ui.theme.Spacing
import kotlin.random.Random
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val revealed by viewModel.revealedIds.collectAsStateWithLifecycle()
    val setupError by viewModel.setupError.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val recoveryError by viewModel.recoveryError.collectAsStateWithLifecycle()
    val recoveryQuestion by viewModel.recoveryQuestion.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("vault", text))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.vault_copied)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (mode == VaultMode.LIST) {
                        TextButton(onClick = { viewModel.lock() }) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                            Text(stringResource(R.string.vault_lock))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (mode) {
                VaultMode.SETUP -> SetupForm(viewModel, setupError)
                VaultMode.UNLOCK -> UnlockForm(viewModel, unlockError, onForgot = { viewModel.startRecovery() })
                VaultMode.RECOVERY -> RecoveryForm(viewModel, recoveryQuestion, recoveryError)
                VaultMode.RECOVERY_NEWPASS -> RecoveryNewPassForm(viewModel, recoveryError)
                VaultMode.LIST -> VaultList(
                    entries = entries,
                    revealed = revealed,
                    onAdd = { viewModel.upsertEntry(it) },
                    onDelete = { viewModel.deleteEntry(it) },
                    onToggleReveal = { viewModel.toggleReveal(it) },
                    onCopy = ::copy,
                )
            }
        }
    }
}

@Composable
private fun FormContainer(content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Composable
private fun SetupForm(viewModel: VaultViewModel, error: String?) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    FormContainer {
        Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.vault_setup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(confirm, { confirm = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_confirm)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_question_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(answer, { answer = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_answer)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.setup(pass, confirm, question, answer) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.vault_set)) }
    }
}

@Composable
private fun UnlockForm(viewModel: VaultViewModel, error: String?, onForgot: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    FormContainer {
        Text(stringResource(R.string.vault_locked), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.unlock(pass) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.vault_unlock)) }
        TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.vault_forgot))
        }
    }
}

@Composable
private fun RecoveryForm(viewModel: VaultViewModel, question: String, error: String?) {
    var answer by remember { mutableStateOf("") }
    FormContainer {
        Text(stringResource(R.string.vault_recovery_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.vault_recovery_question, question), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(answer, { answer = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_recovery_answer_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.submitRecoveryAnswer(answer) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.vault_recovery_confirm)) }
    }
}

@Composable
private fun RecoveryNewPassForm(viewModel: VaultViewModel, error: String?) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    FormContainer {
        Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.vault_recovery_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(confirm, { confirm = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_confirm)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.submitRecoveryNewPass(pass, confirm) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.vault_set)) }
    }
}

@Composable
private fun VaultList(
    entries: List<VaultEntry>,
    revealed: Set<Long>,
    onAdd: (VaultEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleReveal: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.vault_entries_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        VaultEntryCard(
                            entry = entry,
                            revealed = revealed.contains(entry.id),
                            onToggleReveal = { onToggleReveal(entry.id) },
                            onCopy = onCopy,
                            onEdit = { editing = entry; editingIsNew = false },
                            onDelete = { onDelete(entry.id) },
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                editing = VaultEntry(Random.nextLong(), "", "", "", "", "")
                editingIsNew = true
            },
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
            Text(stringResource(R.string.vault_add))
        }
    }

    if (editing != null) {
        EntryEditorDialog(
            initial = editing!!,
            isNew = editingIsNew,
            onDismiss = { editing = null },
            onSave = {
                onAdd(it)
                editing = null
            },
        )
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title.ifBlank { stringResource(R.string.vault_entry_name) }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (entry.username.isNotBlank()) {
                Text("${stringResource(R.string.vault_entry_username)}：${entry.username}", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${stringResource(R.string.vault_entry_password)}：${if (revealed) entry.password else "••••••••"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleReveal) {
                    Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (revealed) R.string.vault_hide else R.string.vault_show), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { if (entry.password.isNotBlank()) onCopy(entry.password) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.vault_copied), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (entry.address.isNotBlank()) {
                Text("${stringResource(R.string.vault_entry_address)}：${entry.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.note.isNotBlank()) {
                Text("${stringResource(R.string.vault_entry_notes)}：${entry.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EntryEditorDialog(
    initial: VaultEntry,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VaultEntry) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var address by remember { mutableStateOf(initial.address) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var note by remember { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(title = title.trim(), address = address.trim(), username = username.trim(), password = password, note = note.trim()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(if (isNew) R.string.vault_add else R.string.action_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_entry_name)) }, singleLine = true)
                OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_entry_address)) }, singleLine = true)
                OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_entry_username)) }, singleLine = true)
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_entry_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_entry_notes)) }, singleLine = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            }
        },
    )
}
