package com.qualitest.ai.llm;

/**
 * LLM 流式输出事件回调。
 * <p>
 * HTTP 客户端在 SSE 解析过程中逐块推送文本或思考内容，结束时回调完整聚合结果。
 */
public interface LlmStreamCallback {

    /** 收到可见文本增量 */
    default void onTextDelta(String delta) {
    }

    /** 收到 Extended Thinking 思考过程增量 */
    default void onThinkingDelta(String delta) {
    }

    /** 流式传输结束，携带完整解析结果（含 tool_calls、usage 等） */
    void onComplete(LlmChatResponse response);
}
