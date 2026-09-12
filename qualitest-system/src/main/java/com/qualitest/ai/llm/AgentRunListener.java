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
     * 全自动：图已隐式写入 test_flow（run 前或回合结束）。
     *
     * @param testFlowId 测试流 id
     */
    default void onGraphCommitted(Long testFlowId) {
    }
}
