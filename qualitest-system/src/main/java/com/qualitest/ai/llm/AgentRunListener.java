package com.qualitest.ai.llm;

/**
 * Agent 运行过程事件回调。
 * 流式设计接口用其把工具起止、文本增量、落盘与会话就绪等推给前端。
 */
public interface AgentRunListener {

    /** 模型开始调用某个工具 */
    default void onToolStart(String toolName) {
    }

    /** 某个工具执行结束 */
    default void onToolEnd(String toolName) {
    }

    /** 模型输出思考过程增量 */
    default void onThinkingDelta(String delta) {
    }

    /** 模型输出正文文本增量 */
    default void onTextDelta(String delta) {
    }

    /**
     * 全自动场景下测试流图已写入数据库。
     *
     * @param testFlowId     已写库的测试流 id
     * @param graphRevision  写入后的图版本号，可空
     */
    default void onGraphCommitted(Long testFlowId, Long graphRevision) {
    }

    /**
     * 全自动场景下已触发一次 Run。
     *
     * @param runId 新产生的运行 id
     */
    default void onRunStarted(Long runId) {
    }

    /**
     * 本轮会话已加载或新建完成，尽早给出会话 id。
     * 客户端取消后可凭此 id 重拉已落盘的助手半成品消息。
     *
     * @param aiChatSessionId 会话主键
     */
    default void onSessionReady(Long aiChatSessionId) {
    }
}
