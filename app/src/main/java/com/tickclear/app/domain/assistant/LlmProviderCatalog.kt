package com.tickclear.app.domain.assistant

/**
 * LLM 服务商目录（多服务商支持，P5.4）。
 *
 * - 仅登记「id + 默认 baseUrl + 默认模型」元数据，用于配置屏切换服务商时回填默认值；
 * - 展示名走 strings.xml（[R.string.assistant_provider_*]），不在域层写死中文；
 * - 小智（xiaozhi）作为默认实现由 [XiaozhiTransport] 承担语音+工具调用，不在 [LlmProvider] 体系内。
 */
object LlmProviderCatalog {
    const val XIAOZHI = "xiaozhi"
    const val OPENAI = "openai"
    const val DOUBAO = "doubao"
    const val QIANWEN = "qianwen"

    /** id → (默认 baseUrl, 默认模型)。小智无 OpenAI 兼容端点，返回 null。 */
    fun defaults(id: String): Pair<String, String>? = when (id) {
        OPENAI -> "https://api.openai.com/v1" to "gpt-4o-mini"
        DOUBAO -> "https://ark.cn-beijing.volces.com/api/v3" to "doubao-seed-1-6"
        QIANWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1" to "qwen-plus"
        else -> null
    }

    /** 可选服务商 id 列表（不含小智，小智在 UI 单独作为默认项）。 */
    val OPENAI_COMPATIBLE = listOf(OPENAI, DOUBAO, QIANWEN)
}
