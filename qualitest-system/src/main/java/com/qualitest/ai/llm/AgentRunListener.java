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
     * 全自动隐式落盘成功后回调（参数为已写库的 testFlowId）。
     * 画布侧可清 Staging 并重新加载该测试流。
     */
    default void onGraphCommitted(Long testFlowId) {
    }

    /**
     * 全自动已触发 Run（参数为刚返回的 runId，图在后台执行中）。
     * 画布侧可开始按步骤轮询详情并高亮当前节点。
     */
    default void onRunStarted(Long runId) {
    }
}
