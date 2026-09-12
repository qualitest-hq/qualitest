package com.qualitest.ai.llm;

/**
 * Agent 运行过程中的可选事件回调，用于 SSE 流式推送。
 */
public interface AgentRunListener {

    /** 模型开始调用某个工具 */
    default void onToolStart(String toolName) {
    }

    /** 某个工具执行结束 */
    default void onToolEnd(String toolName) {
    }

    /** 模型输出思考过程增量（Extended Thinking） */
    default void onThinkingDelta(String delta) {
    }

    /** 模型输出文本增量（通常为最终轮） */
    default void onTextDelta(String delta) {
    }

    /**
     * 全自动隐式落盘成功后回调。
     * 前端据此清 Staging 并重新加载画布（testFlowId 为已写库的测试流）。
     */
    default void onGraphCommitted(Long testFlowId) {
    }
}
