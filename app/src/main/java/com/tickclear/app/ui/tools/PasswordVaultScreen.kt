package com.tickclear.app.ui.tools

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.model.VaultEntry
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.OutlinedButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tickclear.app.data.VaultBioCrypto
import com.tickclear.app.domain.log.AppLogger

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
    val justSetup by viewModel.justSetup.collectAsStateWithLifecycle()
    val bioHardware by viewModel.bioHardware.collectAsStateWithLifecycle()
    val bioBound by viewModel.bioBound.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 添加 / 编辑条目对话框状态提升到屏幕层，供「设置成功 3s 后自动进入添加页」使用。
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    // 设置主口令成功：先提示「请保存你的密码信息」，3 秒后自动弹出添加条目页。
    LaunchedEffect(justSetup) {
        if (justSetup) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.vault_setup_success)) }
            delay(3000)
            editing = VaultEntry(0, "", "", "", "", "")
            editingIsNew = true
            viewModel.consumeSetupCompleted()
        }
    }

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("vault", text)
        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            clip.description.extras?.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        cm.setPrimaryClip(clip)
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.vault_copied)) }
    }

    // ---------------- 生物识别快速解锁 ----------------
    // BiometricPrompt 需要 FragmentActivity 承载；MainActivity 已改为 AppCompatActivity（FragmentActivity 子类）。
    val activity = context as? FragmentActivity
    val executor = ContextCompat.getMainExecutor(context)
    var bioOp by remember { mutableStateOf(BioOp.NONE) }
    val bioPrompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val cipher = result.cryptoObject?.cipher ?: return
                        when (bioOp) {
                            BioOp.UNLOCK -> {
                                val key = VaultBioCrypto.unwrap(context, cipher)
                                viewModel.unlockWithBio(key)
                            }
                            BioOp.BIND -> {
                                viewModel.bindBio(context, cipher)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.vault_bio_enabled_toast),
                                    )
                                }
                            }
                            BioOp.NONE -> {}
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // 用户取消或负按钮：停留在当前页，无需处理。
                    }

                    override fun onAuthenticationFailed() {
                        // 指纹/面容不匹配：系统弹窗会自动重试，无需处理。
                    }
                },
            )
        }
    }
    val bioSupported = bioPrompt != null

    fun launchBioUnlock() {
        val prompt = bioPrompt ?: return
        bioOp = BioOp.UNLOCK
        try {
            val cipher = VaultBioCrypto.prepareDecryptCipher(context)
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.vault_bio_unlock_title))
                    .setSubtitle(context.getString(R.string.vault_bio_unlock_subtitle))
                    .setNegativeButtonText(context.getString(R.string.vault_bio_neg))
                    .build(),
                BiometricPrompt.CryptoObject(cipher),
            )
        } catch (e: Exception) {
            // 密钥失效（生物识别登记变更等）：清理封装并提示重新绑定。
            AppLogger.e("Vault", "bio prepare decrypt failed", e)
            viewModel.unbindBio(context)
        }
    }

    fun launchBioBind() {
        val prompt = bioPrompt ?: return
        bioOp = BioOp.BIND
        val cipher = VaultBioCrypto.prepareEncryptCipher()
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.vault_bio_bind_title))
                .setSubtitle(context.getString(R.string.vault_bio_bind_subtitle))
                .setNegativeButtonText(context.getString(R.string.vault_bio_neg))
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        )
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
                        if (bioHardware) {
                            val bound = bioBound
                            TextButton(onClick = {
                                if (bound) {
                                    viewModel.unbindBio(context)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.vault_bio_disabled_toast),
                                        )
                                    }
                                } else {
                                    launchBioBind()
                                }
                            }) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                                Text(stringResource(if (bound) R.string.vault_bio_disable else R.string.vault_bio_enable))
                            }
                        }
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
                VaultMode.UNLOCK -> UnlockForm(
                    viewModel,
                    unlockError,
                    onForgot = { viewModel.startRecovery() },
                    bioEnabled = bioBound && bioSupported,
                    onBioUnlock = ::launchBioUnlock,
                )
                VaultMode.RECOVERY -> RecoveryForm(viewModel, recoveryQuestion, recoveryError)
                VaultMode.RECOVERY_NEWPASS -> RecoveryNewPassForm(viewModel, recoveryError)
                VaultMode.LIST -> VaultList(
                    entries = entries,
                    revealed = revealed,
                    onAddClick = {
                        editing = VaultEntry(0, "", "", "", "", "")
                        editingIsNew = true
                    },
                    onEditClick = { entry ->
                        editing = entry
                        editingIsNew = false
                    },
                    onDelete = { viewModel.deleteEntry(it) },
                    onToggleReveal = { viewModel.toggleReveal(it) },
                    onCopy = ::copy,
                )
            }
        }

        if (editing != null) {
            EntryEditorDialog(
                initial = editing!!,
                isNew = editingIsNew,
                onDismiss = { editing = null },
                onSave = {
                    viewModel.upsertEntry(it)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun FormContainer(content: @Composable ColumnScope.() -> Unit) {
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
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            repeat(3) {
                shake.animateTo(10f, tween(45))
                shake.animateTo(-10f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

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
            modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.roundToInt(), 0) },
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
            Text(stringResource(R.string.vault_set))
        }
    }
}

@Composable
private fun UnlockForm(
    viewModel: VaultViewModel,
    error: String?,
    onForgot: () -> Unit,
    bioEnabled: Boolean,
    onBioUnlock: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            repeat(3) {
                shake.animateTo(10f, tween(45))
                shake.animateTo(-10f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }
    FormContainer {
        Text(stringResource(R.string.vault_locked), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.unlock(pass) },
            modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.roundToInt(), 0) },
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
            Text(stringResource(R.string.vault_unlock))
        }
        if (bioEnabled) {
            Spacer(Modifier.height(Spacing.xs))
            OutlinedButton(
                onClick = onBioUnlock,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                Text(stringResource(R.string.vault_bio_unlock))
            }
        }
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
    var showConfirm by remember { mutableStateOf(false) }
    FormContainer {
        Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.vault_recovery_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(confirm, { confirm = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vault_confirm)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
            Text(stringResource(R.string.vault_set))
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.submitRecoveryNewPass(pass, confirm)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.vault_recovery_reset_title)) },
            text = { Text(stringResource(R.string.vault_recovery_reset_confirm)) },
        )
    }
}

@Composable
private fun VaultList(
    entries: List<VaultEntry>,
    revealed: Set<Long>,
    onAddClick: () -> Unit,
    onEditClick: (VaultEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleReveal: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
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
                    contentPadding = PaddingValues(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        VaultEntryCard(
                            entry = entry,
                            revealed = revealed.contains(entry.id),
                            onToggleReveal = { onToggleReveal(entry.id) },
                            onCopy = onCopy,
                            onEdit = { onEditClick(entry) },
                            onDelete = { onDelete(entry.id) },
                        )
                    }
                }
            }
        }
        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
            Text(stringResource(R.string.vault_add))
        }
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
        // 收紧内边距 + 缩小图标按钮触摸尺寸，整体面板高度减少约 1/4。
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.title.ifBlank { stringResource(R.string.vault_entry_name) },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (entry.username.isNotBlank()) {
                    Text(
                        "${stringResource(R.string.vault_entry_username)}：${entry.username}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.vault_entry_password)}：${if (revealed) entry.password else "••••••••"}",
                        style = MaterialTheme.typography.bodyMedium,
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
}

@Composable
private fun EntryEditorDialog(
    initial: VaultEntry,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VaultEntry) -> Unit,
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var address by remember(initial.id) { mutableStateOf(initial.address) }
    var username by remember(initial.id) { mutableStateOf(initial.username) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }

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

/** 生物识别操作类型，供 BiometricPrompt 认证成功回调区分本次意图。 */
private enum class BioOp { NONE, UNLOCK, BIND }
