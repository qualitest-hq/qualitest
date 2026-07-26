package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次 LLM 调用的 Token 用量统计。
 * <p>
 * 由 HTTP 客户端从上游响应 {@code usage} 字段解析，用于日志与后续计费扩展。
 */
@Getter
@Builder
public class LlmUsage {

    /** 输入 Token 数（prompt / input_tokens） */
    private final Integer inputTokens;

    /** 输出 Token 数（completion / output_tokens） */
    private final Integer outputTokens;

    /** 从 Prompt Cache 读取的 Token 数（Anthropic cache_read_input_tokens） */
    private final Integer cacheReadTokens;

    /** 写入 Prompt Cache 的 Token 数（Anthropic cache_creation_input_tokens） */
    private final Integer cacheCreationTokens;
}
