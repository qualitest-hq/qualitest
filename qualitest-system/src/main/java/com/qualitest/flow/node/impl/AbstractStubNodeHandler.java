package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodeShellSupport;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点 Handler 骨架：{@code supports} 为真，{@code execute} 返回未实现占位。
 * <p>
 * 不发 HTTP、不修改 {@code ctx}；子类可覆盖 {@link #execute} 实现真实逻辑。
 */
public abstract class AbstractStubNodeHandler implements NodeHandler {

    /** stub 步骤的统一失败消息 */
    private static final String STUB_MESSAGE = "节点执行未实现";

    /** 本 Handler 支持的节点 type 字符串 */
    private final String supportedType;

    /**
     * @param nodeType 支持的节点枚举，取其 {@link FlowNodeType#getCode()}
     */
    protected AbstractStubNodeHandler(FlowNodeType nodeType) {
        this.supportedType = nodeType.getCode();
    }

    @Override
    public boolean supports(String type) {
        return supportedType.equals(type);
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(supportedType)
                .nodeName(resolveNodeName(node))
                .edgeId(incomingEdgeId)
                .status(RunStatus.FAILED.getCode())
                .durationMs(0)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(FlowErrorCode.TF_STEP_ERROR, STUB_MESSAGE))
                .build();
    }

    /**
     * 解析节点展示名：读顶壳 {@code name}；空白时回落类型标签（校验应已拦空白）。
     */
    public static String resolveNodeName(GraphNode node) {
        return GraphNodeShellSupport.resolveNodeName(node);
    }

    /**
     * 浅拷贝当前 {@code flow} 变量，用于填充 {@link StepResult#getFlowAfter()}。
     * 包内可见，供入口等未继承本类的 Handler 复用。
     */
    static Map<String, Object> copyFlow(FlowRunContext ctx) {
        Map<String, Object> flow = ctx != null ? ctx.getFlow() : null;
        if (flow == null || flow.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(flow);
    }
}
