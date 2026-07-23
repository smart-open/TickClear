package com.tickclear.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.LlmProviderCatalog
import com.tickclear.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private val LLM_OPTIONS = listOf(
    LlmProviderCatalog.XIAOZHI to R.string.assistant_provider_xiaozhi,
    LlmProviderCatalog.OPENAI to R.string.assistant_provider_openai,
    LlmProviderCatalog.DOUBAO to R.string.assistant_provider_doubao,
    LlmProviderCatalog.QIANWEN to R.string.assistant_provider_qianwen,
)

private val ASR_OPTIONS = listOf(
    AsrProviderCatalog.XIAOZHI to R.string.assistant_asr_xiaozhi,
    AsrProviderCatalog.OPENAI to R.string.assistant_asr_openai,
    AsrProviderCatalog.TENCENT to R.string.assistant_asr_tencent,
    AsrProviderCatalog.ALIYUN to R.string.assistant_asr_aliyun,
    AsrProviderCatalog.SYSTEM to R.string.assistant_asr_system,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigSheet(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val mode by settingsViewModel.assistantMode.collectAsStateWithLifecycle()
    val endpoint by settingsViewModel.assistantEndpoint.collectAsStateWithLifecycle()
    val prompt by settingsViewModel.assistantPrompt.collectAsStateWithLifecycle()
    val provider by settingsViewModel.llmProvider.collectAsStateWithLifecycle()
    val baseUrl by settingsViewModel.llmBaseUrl.collectAsStateWithLifecycle()
    val model by settingsViewModel.llmModel.collectAsStateWithLifecycle()
    val asrProvider by settingsViewModel.asrProvider.collectAsStateWithLifecycle()
    val asrBaseUrl by settingsViewModel.asrBaseUrl.collectAsStateWithLifecycle()
    val asrModel by settingsViewModel.asrModel.collectAsStateWithLifecycle()
    val wakeEnabled by settingsViewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val wakeWord by settingsViewModel.wakeWord.collectAsStateWithLifecycle()
    val trustMode by settingsViewModel.trustMode.collectAsStateWithLifecycle()

    var localMode by remember { mutableStateOf(mode) }
    var localEndpoint by remember { mutableStateOf(endpoint) }
    var localPrompt by remember { mutableStateOf(prompt) }
    var localToken by remember { mutableStateOf(SecureStore.getSecret(context, SettingsRepository.PREF_XZ_TOKEN).orEmpty()) }
    var localProvider by remember { mutableStateOf(provider) }
    var localBaseUrl by remember { mutableStateOf(baseUrl) }
    var localModel by remember { mutableStateOf(model) }
    var localApiKey by remember { mutableStateOf("") }
    var localAsrProvider by remember { mutableStateOf(asrProvider) }
    var localAsrBaseUrl by remember { mutableStateOf(asrBaseUrl) }
    var localAsrModel by remember { mutableStateOf(asrModel) }
    var localAsrApiKey by remember { mutableStateOf("") }
    var localTencentSecretId by remember { mutableStateOf("") }
    var localTencentSecretKey by remember { mutableStateOf("") }
    var localAliyunAccessKeyId by remember { mutableStateOf("") }
    var localAliyunAccessKeySecret by remember { mutableStateOf("") }
    var localAliyunAppKey by remember { mutableStateOf("") }
    var localWakeEnabled by remember { mutableStateOf(wakeEnabled) }
    var localWakeWord by remember { mutableStateOf(wakeWord) }
    var localTrust by remember { mutableStateOf(trustMode) }
    var asrTestMsg by remember { mutableStateOf<String?>(null) }

    // 切换 LLM 服务商：载入该服务商默认值与密钥
    LaunchedEffect(localProvider) {
        val def = LlmProviderCatalog.defaults(localProvider)
        if (def != null) {
            localBaseUrl = def.first
            localModel = def.second
        }
        localApiKey = settingsViewModel.getLlmApiKey(localProvider).orEmpty()
    }
    // 切换 ASR 服务商：载入该服务商凭据与默认值
    LaunchedEffect(localAsrProvider) {
        localAsrBaseUrl = AsrProviderCatalog.defaultBaseUrl(localAsrProvider) ?: ""
        localAsrModel = if (localAsrProvider == AsrProviderCatalog.OPENAI) "whisper-1" else ""
        localAsrApiKey = if (localAsrProvider == AsrProviderCatalog.OPENAI) {
            settingsViewModel.getAsrApiKey().orEmpty()
        } else ""
        localTencentSecretId = settingsViewModel.getTencentSecretId().orEmpty()
        localTencentSecretKey = settingsViewModel.getTencentSecretKey().orEmpty()
        localAliyunAccessKeyId = settingsViewModel.getAliyunAccessKeyId().orEmpty()
        localAliyunAccessKeySecret = settingsViewModel.getAliyunAccessKeySecret().orEmpty()
        localAliyunAppKey = settingsViewModel.getAliyunAppKey().orEmpty()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.assistant_config_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )

            // ── LLM 服务商 ──
            Text(stringResource(R.string.assistant_provider_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LLM_OPTIONS.forEach { (id, labelRes) ->
                    FilterChip(
                        selected = localProvider == id,
                        onClick = { localProvider = id },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            OutlinedTextField(
                value = localPrompt,
                onValueChange = { localPrompt = it },
                label = { Text(stringResource(R.string.assistant_prompt_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            if (localProvider == LlmProviderCatalog.XIAOZHI) {
                Text(stringResource(R.string.assistant_mode_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = localMode == "MOCK",
                        onClick = { localMode = "MOCK" },
                        label = { Text(stringResource(R.string.assistant_mode_mock)) },
                    )
                    FilterChip(
                        selected = localMode == "REAL",
                        onClick = { localMode = "REAL" },
                        label = { Text(stringResource(R.string.assistant_mode_real_chip)) },
                    )
                }
                OutlinedTextField(
                    value = localEndpoint,
                    onValueChange = { localEndpoint = it },
                    label = { Text(stringResource(R.string.assistant_endpoint_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = localToken,
                    onValueChange = { localToken = it },
                    label = { Text(stringResource(R.string.assistant_token_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            } else {
                OutlinedTextField(
                    value = localBaseUrl,
                    onValueChange = { localBaseUrl = it },
                    label = { Text(stringResource(R.string.assistant_base_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = localApiKey,
                    onValueChange = { localApiKey = it },
                    label = { Text(stringResource(R.string.assistant_api_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                OutlinedTextField(
                    value = localModel,
                    onValueChange = { localModel = it },
                    label = { Text(stringResource(R.string.assistant_model_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ── ASR 服务商（语音识别后端）──
            Text(stringResource(R.string.assistant_asr_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ASR_OPTIONS.forEach { (id, labelRes) ->
                    FilterChip(
                        selected = localAsrProvider == id,
                        onClick = { localAsrProvider = id },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            when (localAsrProvider) {
                AsrProviderCatalog.OPENAI -> {
                    AsrTextField(localAsrBaseUrl) { localAsrBaseUrl = it }
                    AsrTextField(localAsrApiKey) { localAsrApiKey = it }
                    AsrTextField(localAsrModel) { localAsrModel = it }
                }
                AsrProviderCatalog.TENCENT -> {
                    AsrTextField(localTencentSecretId, stringResource(R.string.assistant_tencent_secret_id_label)) { localTencentSecretId = it }
                    AsrTextField(localTencentSecretKey, stringResource(R.string.assistant_tencent_secret_key_label)) { localTencentSecretKey = it }
                }
                AsrProviderCatalog.ALIYUN -> {
                    AsrTextField(localAliyunAccessKeyId, stringResource(R.string.assistant_aliyun_access_key_label)) { localAliyunAccessKeyId = it }
                    AsrTextField(localAliyunAccessKeySecret, stringResource(R.string.assistant_aliyun_access_secret_label)) { localAliyunAccessKeySecret = it }
                    AsrTextField(localAliyunAppKey, stringResource(R.string.assistant_aliyun_app_key_label)) { localAliyunAppKey = it }
                }
                AsrProviderCatalog.SYSTEM -> {
                    Text(stringResource(R.string.assistant_asr_system_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                else -> { /* xiaozhi：由小智服务端识别，无额外配置 */ }
            }

            // ── ASR 测试连接（仅文件式云 ASR 显示）──
            if (localAsrProvider in setOf(AsrProviderCatalog.OPENAI, AsrProviderCatalog.TENCENT, AsrProviderCatalog.ALIYUN)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        scope.launch {
                            settingsViewModel.persistAsrForTest(
                                localAsrProvider, localAsrBaseUrl, localAsrModel, localAsrApiKey,
                                localTencentSecretId, localTencentSecretKey,
                                localAliyunAccessKeyId, localAliyunAccessKeySecret, localAliyunAppKey,
                            )
                            asrTestMsg = if (settingsViewModel.testCurrentAsr()) {
                                context.getString(R.string.assistant_asr_test_ok)
                            } else {
                                context.getString(R.string.assistant_asr_test_fail)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.assistant_asr_test))
                    }
                    asrTestMsg?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                }
            }

            // ── 语音唤醒词（离线 best-effort）──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.assistant_wakeword_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.assistant_wakeword_subtitle), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Switch(checked = localWakeEnabled, onCheckedChange = { localWakeEnabled = it })
            }
            if (localWakeEnabled) {
                OutlinedTextField(
                    value = localWakeWord,
                    onValueChange = { localWakeWord = it },
                    label = { Text(stringResource(R.string.assistant_wakeword_phrase_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ── 信任模式（免确认创建）──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.assistant_trust_title), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.assistant_trust_subtitle), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Switch(checked = localTrust, onCheckedChange = { localTrust = it })
            }

            Button(
                onClick = {
                    scope.launch {
                        settingsViewModel.setTrustMode(localTrust)
                        settingsViewModel.setLlmProvider(localProvider)
                        settingsViewModel.setAssistantPrompt(localPrompt)
                        if (localProvider == LlmProviderCatalog.XIAOZHI) {
                            settingsViewModel.setAssistantMode(localMode)
                            settingsViewModel.setAssistantEndpoint(localEndpoint)
                            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_TOKEN, localToken)
                        } else {
                            settingsViewModel.setLlmBaseUrl(localBaseUrl)
                            settingsViewModel.setLlmModel(localModel)
                            settingsViewModel.setLlmApiKey(localProvider, localApiKey)
                        }
                        settingsViewModel.setAsrProvider(localAsrProvider)
                        when (localAsrProvider) {
                            AsrProviderCatalog.OPENAI -> {
                                settingsViewModel.setAsrBaseUrl(localAsrBaseUrl)
                                settingsViewModel.setAsrModel(localAsrModel)
                                settingsViewModel.setAsrApiKey(localAsrApiKey)
                            }
                            AsrProviderCatalog.TENCENT -> {
                                settingsViewModel.setTencentSecretId(localTencentSecretId)
                                settingsViewModel.setTencentSecretKey(localTencentSecretKey)
                            }
                            AsrProviderCatalog.ALIYUN -> {
                                settingsViewModel.setAliyunAccessKeyId(localAliyunAccessKeyId)
                                settingsViewModel.setAliyunAccessKeySecret(localAliyunAccessKeySecret)
                                settingsViewModel.setAliyunAppKey(localAliyunAppKey)
                            }
                        }
                        settingsViewModel.setWakeWordEnabled(localWakeEnabled)
                        settingsViewModel.setWakeWord(localWakeWord)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun AsrTextField(
    value: String,
    label: String = stringResource(R.string.assistant_api_key_label),
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}
