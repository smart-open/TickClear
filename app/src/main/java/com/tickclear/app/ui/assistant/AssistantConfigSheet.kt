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
    var xzSerialNumber by remember { mutableStateOf("") }
    var otaCode by remember { mutableStateOf<String?>(null) }          // 6 位验证码
    var otaMessage by remember { mutableStateOf<String?>(null) }      // 状态消息
    var otaIsError by remember { mutableStateOf(false) }              // 状态消息是否错误态（驱动颜色，避免嗅探本地化文案）
    var otaLoading by remember { mutableStateOf(false) }
    var otaDone by remember { mutableStateOf(false) }                  // 是否已执行过 OTA
    // V2.8X 连接测试：UI 触发后调用 XiaozhiConnectionTester，返回 Ok/Fail 结果。
    var testLoading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<com.tickclear.app.domain.assistant.XiaozhiConnectionTester.Result?>(null) }

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
            val (did, cid, sn) = settingsViewModel.ensureXzDeviceIdentity()
            xzDeviceId = did
            xzClientId = cid
            xzSerialNumber = sn
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
                    // Device-Id：可手动编辑（V2.8X+）。可从 xiaozhi.me 控制台「我的设备」抄入【已绑定设备】的 MAC，
                    // 服务端鉴权仅基于 device-id（client-id 不参与校验、不读 Authorization 头），
                    // 粘贴已知可用的 Device-Id 即可让 App 以该设备身份完成握手，用于验证 App 协议是否正确。
                    // V2.8X++：Device-Id 必须由用户输入真实设备 MAC，并做格式校验。
                    // 实时归一化（去空白/转大写/连字符转冒号，12 位连续 hex 自动补冒号）以便校验与激活复用。
                    val xzMacValid = xzDeviceId.isBlank() || XiaozhiDeviceSimulator.isValidMacAddress(xzDeviceId)
                    OutlinedTextField(
                        value = xzDeviceId,
                        onValueChange = { newId ->
                            xzDeviceId = XiaozhiDeviceSimulator.normalizeMac(newId)
                            scope.launch { settingsViewModel.setXzDeviceId(xzDeviceId) }
                        },
                        label = { Text(stringResource(R.string.assistant_device_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !xzMacValid,
                        supportingText = if (!xzMacValid) {
                            { Text(stringResource(R.string.assistant_mac_invalid)) }
                        } else null,
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
                    Text(
                        text = stringResource(R.string.assistant_device_id_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Client-Id：可手动编辑（V2.8X+）。服务端不参与校验（仅作 API 第二参数、缺省回退为 device-id），
                    // 一般无需改动；保留可编辑以便对照测试时按需粘贴。
                    OutlinedTextField(
                        value = xzClientId,
                        onValueChange = { newId ->
                            xzClientId = newId
                            scope.launch { settingsViewModel.setXzClientId(newId) }
                        },
                        label = { Text(stringResource(R.string.assistant_client_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
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
                                    otaIsError = false
                                    otaCode = null
                                    otaDone = false
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
                                    // V2.8X++ py-xiaozhi 同款激活流程：
                                    // Step1 拿 code 后立即展示（onActivationCode 回调），用户此时去官网输码；
                                    // Step2 在后台持续轮询 /activate（202=等输码，最多 5 分钟），
                                    // 官网绑定校验的 serial_number 正是该轮询请求提交的 —— 轮询必须保持在线。
                                    // V2.8X++：激活前用归一化后的真实 MAC 派生 serial_number 并持久化，
                                    // 确保走激活流程的身份与用户输入一致（旧的 xzSerialNumber 可能基于旧 MAC）。
                                    val cleanMac = XiaozhiDeviceSimulator.normalizeMac(xzDeviceId)
                                    settingsViewModel.setXzDeviceId(cleanMac)
                                    xzDeviceId = cleanMac
                                    val sn = XiaozhiDeviceSimulator.generateSerialNumber(cleanMac)
                                    settingsViewModel.setXzSerialNumber(sn)
                                    xzSerialNumber = sn
                                    val result = XiaozhiDeviceSimulator.activateDeviceTwoStep(
                                        deviceId = cleanMac,
                                        clientId = xzClientId,
                                        serialNumber = sn,
                                        otaUrl = otaUrl,
                                        onActivationCode = { code ->
                                            otaCode = code
                                            otaMessage = context.getString(R.string.assistant_two_step_waiting)
                                            otaIsError = false
                                        },
                                    )
                                    otaLoading = false
                                    otaDone = true
                                    if (!result.websocketUrl.isNullOrBlank()) {
                                        localEndpoint = result.websocketUrl
                                        settingsViewModel.setAssistantEndpoint(result.websocketUrl)
                                    }
                                    // OTA 下发的 WS token 自动保存（官方云通常为 "test-token"，自建服务端可能是真 token）
                                    if (!result.websocketToken.isNullOrBlank()) {
                                        localToken = result.websocketToken
                                        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_TOKEN, result.websocketToken)
                                    }
                                    // 激活成功后清空 6 位码（绑定已完成，无需再输）
                                    otaCode = if (result.isActivated) null else result.code
                                    // 文案按 step1 / step2 分状态给精准反馈；同时用结构化字段标记错误态，
                                    // 颜色据此渲染，不再嗅探本地化文案里的「失败/error」子串（那会让
                                    // 「激活轮询异常：HTTP 500: ...」这类真实错误误显为成功色，P3）。
                                    val (msg, isErr) = when {
                                        result.isActivated ->
                                            context.getString(R.string.assistant_two_step_ok) to false
                                        result.step1Error != null ->
                                            context.getString(R.string.assistant_two_step_step1_error, result.step1Error) to true
                                        result.step2Status == "TIMEOUT" ->
                                            context.getString(R.string.assistant_two_step_timeout) to true
                                        result.step2Error != null ->
                                            context.getString(R.string.assistant_two_step_step2_error, result.step2Error) to true
                                        else ->
                                            context.getString(R.string.assistant_two_step_unknown) to true
                                    }
                                    otaMessage = msg
                                    otaIsError = isErr
                                }
                            },
                            enabled = !otaLoading && xzDeviceId.isNotBlank() && XiaozhiDeviceSimulator.isValidMacAddress(xzDeviceId),
                        ) {
                            if (otaLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.assistant_activate_two_step_btn))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val (newDid, newCid, newSn) = settingsViewModel.regenerateXzDeviceIdentity()
                                    xzDeviceId = newDid
                                    xzClientId = newCid
                                    xzSerialNumber = newSn
                                    otaCode = null
                                    otaMessage = null
                                    otaIsError = false
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
                                color = if (otaIsError)
                                    androidx.compose.material3.MaterialTheme.colorScheme.error
                                else
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    // V2.8X 「测试连接」：用当前 endpoint/token/device 走一次握手，零副作用探针。
                    // 用户已绑定官网但仍「握手超时」时，按此按钮即时拿到诊断（端点不可达 / Device-Id 未绑定 / 协议不匹配），
                    // 不必每次打开助手页等满 10s 握手超时。
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = stringResource(R.string.assistant_test_section_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    testLoading = true
                                    testResult = null
                                    // 即时把当前 endpoint 持久化后再测试，避免「填了新地址但未保存」导致测的是旧值。
                                    settingsViewModel.setAssistantEndpoint(localEndpoint)
                                    testResult = settingsViewModel.testXiaozhiConnection()
                                    testLoading = false
                                }
                            },
                            enabled = !testLoading && xzDeviceId.isNotBlank() && XiaozhiDeviceSimulator.isValidMacAddress(xzDeviceId),
                        ) {
                            if (testLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(R.string.assistant_test_btn))
                            }
                        }
                        testResult?.let { r ->
                            Text(
                                text = when (r) {
                                    is com.tickclear.app.domain.assistant.XiaozhiConnectionTester.Result.Ok ->
                                        context.getString(
                                            R.string.assistant_test_ok,
                                            r.endpoint,
                                            r.sessionId ?: "-",
                                            r.sampleRate ?: 0,
                                        )
                                    is com.tickclear.app.domain.assistant.XiaozhiConnectionTester.Result.Fail ->
                                        context.getString(R.string.assistant_test_fail, r.reason)
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = when (r) {
                                    is com.tickclear.app.domain.assistant.XiaozhiConnectionTester.Result.Ok ->
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    is com.tickclear.app.domain.assistant.XiaozhiConnectionTester.Result.Fail ->
                                        androidx.compose.material3.MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f),
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
