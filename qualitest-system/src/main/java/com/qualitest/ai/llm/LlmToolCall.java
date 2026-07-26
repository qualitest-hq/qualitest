package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 模型在 assistant 回复中发起的一次工具调用。
 * <p>
 * 调用方执行工具后，需以相同 {@link #id} 构造 tool 消息回传结果。
 */
@Getter
@Builder
public class LlmToolCall {

    /** 工具调用唯一 id，tool 消息通过此 id 关联 */
    private final String id;

    /** 工具名称，与 tools 定义中的 name 对应 */
    private final String name;

    /** 工具参数 JSON 字符串，由模型生成 */
    private final String argumentsJson;
}
