package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;

/**
 * 测试流节点执行器。
 * <p>
 * 由 {@link NodeHandlerRegistry} 按 {@link com.qualitest.flow.model.GraphNode#getType()} 路由；
 * 输入为图节点与 {@link FlowRunContext}，输出为 {@link StepResult}。
 */
public interface NodeHandler {

    /**
     * 是否支持给定节点 type。
     */
    boolean supports(String type);

    /**
     * 执行单步并返回结果。
     *
     * @param ctx             运行时上下文
     * @param node            已迁移的图节点
     * @param incomingEdgeId  入边 id，开始节点为 null
     */
    StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId);
}
