package com.qualitest.ai.mcp.protocol;

import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.flow.model.GraphJson;

/**
 * 一条 MCP Streamable HTTP 会话。
 * <p>
 * 逐次调用（{@link Mode#CALL}）：每次工具调用各自提交，submit 校验通过就写入测试流。
 * 回合会话（{@link Mode#TURN}）：绑定一张已打开的测试流，同一会话内累积已接受的设计单元和工作图；
 * submit 只表示已收下，调用 run_test_flow 前才把这些单元写入测试流。
 * 会话被移除、断线或超时时，只丢掉还留在内存里、尚未跑流的单元，不把半成品写入测试流。
 */
public final class McpSession {

    /** 会话怎么处理设计单元。 */
    public enum Mode {
        /** 每次调用单独提交到测试流 */
        CALL,
        /** 同一会话内累积，跑流前写入测试流 */
        TURN
    }

    /** 会话 id，放在响应头和后续请求头 Mcp-Session-Id 里 */
    private final String id;
    /** 这条连接所属的测试项目 */
    private final Long testProjectId;
    /** 逐次调用或回合会话 */
    private final Mode mode;
    /** 回合会话绑定的测试流；逐次调用为 null */
    private final Long testFlowId;
    /** 本会话已接受、尚未跑流的设计单元。逐次调用不使用这份累积器 */
    private final FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
    /** 回合会话当前工作图。还没执行过工具时为 null，此时从库中的测试流加载 */
    private volatile GraphJson graph;
    /** 工作图对应的画布版本，跑流落盘成功后更新 */
    private volatile Long revision;

    /**
     * 建立会话。mode 为空时按逐次调用。
     * 回合会话的测试流 id 由调用方保证已给出。
     */
    public McpSession(String id, Long testProjectId, Mode mode, Long testFlowId) {
        this.id = id;
        this.testProjectId = testProjectId;
        this.mode = mode == null ? Mode.CALL : mode;
        this.testFlowId = testFlowId;
    }

    /** 会话 id。 */
    public String getId() {
        return id;
    }

    /** 会话所属测试项目。 */
    public Long getTestProjectId() {
        return testProjectId;
    }

    /** 逐次调用或回合会话。 */
    public Mode getMode() {
        return mode;
    }

    /** 是否为在同一会话内累积设计单元的回合会话。 */
    public boolean isTurn() {
        return mode == Mode.TURN;
    }

    /** 回合会话绑定的测试流；逐次调用会话为 null。 */
    public Long getTestFlowId() {
        return testFlowId;
    }

    /** 本会话共用的设计单元累积器。 */
    public FlowDesignSubmitCapture getCapture() {
        return capture;
    }

    /** 回合会话当前工作图；尚无累积结果时为 null。 */
    public GraphJson getGraph() {
        return graph;
    }

    /** 记下本次工具执行后的工作图，供下一次调用继续。 */
    public void setGraph(GraphJson graph) {
        this.graph = graph;
    }

    /** 工作图对应的画布版本。 */
    public Long getRevision() {
        return revision;
    }

    /** 更新工作图版本。跑流前落盘成功后由执行结果回写。 */
    public void setRevision(Long revision) {
        this.revision = revision;
    }
}
