package com.tickclear.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.LlmProviderCatalog
import com.tickclear.app.domain.assistant.XiaozhiDeviceSimulator
import com.tickclear.app.ui.assistant.AssistantViewModel
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantConfigSheet(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // V2.8X：传入助手 VM（懒加载），保存时用它触发 reconnect，让 endpoint/token/ASR 即时生效。
        val assistantVm: AssistantViewModel = hiltViewModel()
        AssistantConfigContent(
            settingsViewModel = settingsViewModel,
            assistantViewModel = assistantVm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * 助手配置面板主体（不含 ModalBottomSheet 外壳）。
 * 既用于窄屏的 BottomSheet，也用于宽屏的常驻侧栏（V2.19）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantConfigContent(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    assistantViewModel: AssistantViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // ── V2.8 小智设备模拟状态 ──
    var xzDeviceId by remember { mutableStateOf("") }
    var xzClientId by remember { mutableStateOf("") }
    var otaCode by remember { mutableStateOf<String?>(null) }          // 6 位验证码
    var otaMessage by remember { mutableStateOf<String?>(null) }      // 状态消息
    var otaLoading by remember { mutableStateOf(false) }
    var otaDone by remember { mutableStateOf(false) }                  // 是否已执行过 OTA

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
    // V2.8：切换到小智时自动加载设备标识
    LaunchedEffect(localProvider) {
        if (localProvider == LlmProviderCatalog.XIAOZHI) {
            val (did, cid) = settingsViewModel.ensureXzDeviceIdentity()
            xzDeviceId = did
            xzClientId = cid
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            Text(
                stringResource(R.string.assistant_config_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )

            // ── LLM 服务商 ──
            Text(stringResource(R.string.assistant_provider_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LLM_OPTIONS.forEach { (id, labelRes) ->
                    FilterChip(
                        selected = localProvider == id,
                        onClick = { localProvider = id },
                        label = { Text(stringResource(labelRes), maxLines = 1) },
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

                // ── V2.8 ESP32 设备模拟（仅 REAL 模式显示）──
                if (localMode == "REAL") {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = stringResource(R.string.assistant_device_section_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                    // Device-Id 显示 + 复制
                    OutlinedTextField(
                        value = xzDeviceId,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.assistant_device_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            // 零新依赖红线：ContentCopy 图标在 material-icons-extended 中，改用文字按钮。
                            TextButton(onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Device-Id", xzDeviceId))
                            }) {
                                Text(stringResource(R.string.assistant_copy_id))
                            }
                        },
                    )
                    // Client-Id 显示 + 复制
                    OutlinedTextField(
                        value = xzClientId,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.assistant_client_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            TextButton(onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Client-Id", xzClientId))
                            }) {
                                Text(stringResource(R.string.assistant_copy_id))
                            }
                        },
                    )
                    // 操作按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    otaLoading = true
                                    otaMessage = null
                                    // OTA 端点：官方（xiaozhi.me/tenclass）固定用 DEFAULT_OTA_URL；
                                    // 自建服务器按约定路径 /xiaozhi/ota/ 从 WS 地址推导。
                                    val otaUrl = if (localEndpoint.isBlank() ||
                                        localEndpoint.contains("xiaozhi.me") ||
                                        localEndpoint.contains("tenclass.net")
                                    ) {
                                        XiaozhiDeviceSimulator.DEFAULT_OTA_URL
                                    } else {
                                        localEndpoint
                                            .replace("wss://", "https://")
                                            .replace("ws://", "http://")
                                            .substringBefore("/xiaozhi")
                                            .trimEnd('/') + "/xiaozhi/ota/"
                                    }
                                    val result = XiaozhiDeviceSimulator.activateDevice(
                                        deviceId = xzDeviceId,
                                        clientId = xzClientId,
                                        otaUrl = otaUrl,
                                    )
                                    otaLoading = false
                                    otaDone = true
                                    // 协程 onClick 内非 Composable 上下文：必须用 context.getString 而非 stringResource。
                                    if (result.error != null) {
                                        otaMessage = context.getString(R.string.assistant_activation_error, result.error)
                                        otaCode = null
                                    } else {
                                        otaCode = result.code
                                        // V2.8X：OTA 响应带回权威 WebSocket 地址，自动填入并持久化 endpoint，
                                        // 避免手填/遗留错误主机（wss://api.xiaozhi.me/ws 不存在）导致一直「未连接」。
                                        if (!result.websocketUrl.isNullOrBlank()) {
                                            localEndpoint = result.websocketUrl
                                            settingsViewModel.setAssistantEndpoint(result.websocketUrl)
                                        }
                                        otaMessage = if (result.needsBinding) {
                                            context.getString(R.string.assistant_activation_success)
                                        } else {
                                            context.getString(R.string.assistant_activation_already_bound)
                                        }
                                    }
                                }
                            },
                            enabled = !otaLoading && xzDeviceId.isNotEmpty(),
                        ) {
                            if (otaLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.assistant_activate_btn))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val (newDid, newCid) = settingsViewModel.regenerateXzDeviceIdentity()
                                    xzDeviceId = newDid
                                    xzClientId = newCid
                                    otaCode = null
                                    otaMessage = null
                                    otaDone = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.assistant_regenerate_btn))
                        }
                    }
                    // 验证码 / 状态消息展示
                    if (otaMessage != null) {
                        if (otaCode != null) {
                            // 验证码大字展示
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .background(
                                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    )
                                    .padding(16.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.assistant_activation_code_title),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = otaCode!!,
                                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    letterSpacing = 4.sp,
                                )
                                Text(
                                    text = stringResource(R.string.assistant_activation_hint),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        } else {
                            // 普通状态消息（已绑定 / 错误）
                            Text(
                                text = otaMessage!!,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = if (otaMessage!!.contains("失败") || otaMessage!!.contains("error"))
                                    androidx.compose.material3.MaterialTheme.colorScheme.error
                                else
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ASR_OPTIONS.forEach { (id, labelRes) ->
                    FilterChip(
                        selected = localAsrProvider == id,
                        onClick = { localAsrProvider = id },
                        label = { Text(stringResource(labelRes), maxLines = 1) },
                    )
                }
            }
            when (localAsrProvider) {
                AsrProviderCatalog.OPENAI -> {
                    AsrTextField(localAsrBaseUrl, stringResource(R.string.assistant_base_url_label)) { localAsrBaseUrl = it }
                    AsrTextField(localAsrApiKey, stringResource(R.string.assistant_api_key_label)) { localAsrApiKey = it }
                    AsrTextField(localAsrModel, stringResource(R.string.assistant_model_label)) { localAsrModel = it }
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
                    asrTestMsg?.let {
                        Text(
                            it,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                        // V2.8X：保存后通知助手 VM 用新配置重连，否则旧 transport 仍指向旧 endpoint/token，
                        // 表现为「一致值未连接、回答还是本地」。连接 / voiceSupported 状态在 VM 内统一刷新。
                        assistantViewModel.reconnectAfterConfig()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
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
