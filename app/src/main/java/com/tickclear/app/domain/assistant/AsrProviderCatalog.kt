package com.tickclear.app.domain.assistant

/**
 * ASR 服务商目录（多服务商支持，P5.5）。
 *
 * - 仅登记「id + 默认 baseUrl」元数据，用于配置屏切换服务商时回填默认值；
 * - 展示名走 strings.xml（[R.string.assistant_asr_*]），不在域层写死中文；
 * - 小智（xiaozhi）由小智服务端完成识别，不实现文件式 [AsrProvider]；
 * - system 为系统框架本地识别（[LocalSpeechRecognizer]），不走文件上传。
 */
object AsrProviderCatalog {
    const val XIAOZHI = "xiaozhi"
    const val OPENAI = "openai"
    const val TENCENT = "tencent"
    const val ALIYUN = "aliyun"
    const val SYSTEM = "system"

    /** 文件式上传后端（走 [AsrProvider.transcribe]）的 id 列表。 */
    val FILE_BASED = listOf(OPENAI, TENCENT, ALIYUN)

    /** id → 默认 baseUrl；小智/system 非文件上传，返回 null。 */
    fun defaultBaseUrl(id: String): String? = when (id) {
        OPENAI -> "https://api.openai.com/v1"
        TENCENT -> "https://asr.tencentcloudapi.com"
        ALIYUN -> "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr"
        else -> null
    }
}
