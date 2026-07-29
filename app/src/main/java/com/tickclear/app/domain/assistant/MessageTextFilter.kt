package com.tickclear.app.domain.assistant

/**
 * V2.8X：消息文本展示层过滤器。
 *
 * 背景：小智官方服务端在 LLM/TTS 文本中会插入多模态资源引用 token（用于 TTS 朗读时插入
 * 图片/表情包合成播放），形如 `@image#<index>:<hash>.jpg`。TTS 音频路径会把它念成
 * "图片"，但**给 UI 展示用的 text 字段是带 token 的原文**，直接显示给用户会出现
 * `@image#1:23e150c4...jpg` 这种纯技术串。
 *
 * 策略：源头（WebSocketXiaozhiTransport 的 llm/tts 帧）+ 防御（AssistantViewModel.onEvent）
 * 双层调用本过滤器，仅剥离多模态 token 保留可读中文，不做语义改写。
 *
 * 边界：TTS 二进制路径不走此 filter（音频无 text 字段）；MCP 工具回执是 system 消息，
 * 由 AssistantViewModel 单独处理，不混入 LlmText。
 */
object MessageTextFilter {

    /**
     * 匹配多模态资源引用 token：
     * - `@image#1:23e150c406a50b91e200450bf3d94b31.jpg`（小智官方格式）
     * - 兼容常见后缀：jpg / jpeg / png / gif / webp
     * - hash 段兼容 32 位 MD5 / 40 位 SHA1 / 64 位 SHA256（按 hex 长度可变性匹配）
     */
    private val IMAGE_TOKEN = Regex("""@image#\d+:[0-9a-fA-F]{8,128}\.(?:jpg|jpeg|png|gif|webp)""")

    /**
     * 去除多模态 token，合并多余空白，返回已 trim 的可展示文本。
     * 返回空串表示「该消息无可展示内容」，调用方应**不要**继续 append。
     *
     * 例：
     * - "你好 @image#1:abc.jpg 世界" → "你好 世界"
     * - "@image#1:abc.jpg @image#2:def.png" → ""（全部是 token）
     * - "听不出来是谁呢，不过没关系！" → "听不出来是谁呢，不过没关系！"（无 token 不动）
     */
    fun strip(s: String): String = s.replace(IMAGE_TOKEN, "")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .replace(Regex("""\s*[\r\n]+\s*"""), " ")
        .trim()
}
