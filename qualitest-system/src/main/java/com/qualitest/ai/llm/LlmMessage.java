package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent 与 LLM 交互使用的单条对话消息（应用层模型）。
 * <p>
 * 支持四种角色：
 * <ul>
 *   <li>{@code system} — 系统指令</li>
 *   <li>{@code user} — 用户输入，可为纯文本或多模态 contentParts</li>
 *   <li>{@code assistant} — 模型回复，可携带 toolCalls 请求宿主执行工具</li>
 *   <li>{@code tool} — 工具执行结果，通过 toolCallId 关联 assistant 发出的调用</li>
 * </ul>
 */
@Getter
@Builder
public class LlmMessage {

    /** 消息角色：system / user / assistant / tool */
    private final String role;

    /** 纯文本内容；与 contentParts 二选一，contentParts 优先 */
    private final String content;

    /** 多模态内容块列表 */
    private final List<LlmContentPart> contentParts;

    /** tool 消息专用：对应一次 tool_call / tool_use 的 id */
    private final String toolCallId;

    /** tool 消息专用：标记工具执行失败，上游可据此调整后续推理 */
    @Builder.Default
    private final boolean toolError = false;

    /** assistant 消息专用：模型请求宿主执行的工具调用列表 */
    private final List<LlmToolCall> toolCalls;

    public static LlmMessage system(String content) {
        return LlmMessage.builder().role("system").content(content).build();
    }

    public static LlmMessage user(String content) {
        return LlmMessage.builder().role("user").content(content).build();
    }

    public static LlmMessage userParts(List<LlmContentPart> parts) {
        return LlmMessage.builder().role("user").contentParts(parts).build();
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return LlmMessage.builder().role("assistant").content(content).toolCalls(toolCalls).build();
    }

    public static LlmMessage tool(String toolCallId, String content) {
        return LlmMessage.builder().role("tool").toolCallId(toolCallId).content(content).build();
    }

    /** 构造 tool 消息，并标记执行是否失败 */
    public static LlmMessage tool(String toolCallId, String content, boolean toolError) {
        return LlmMessage.builder().role("tool").toolCallId(toolCallId).content(content).toolError(toolError).build();
    }
}
