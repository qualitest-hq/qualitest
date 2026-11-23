package com.qualitest.flow.node.impl;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

/**
 * 入口节点透传执行器。
 * <p>
 * 支持 {@code scenario-entry} / {@code subflow-entry} / {@code http-entry}：
 * <ul>
 *   <li>若上下文尚未记录 {@code entryNodeId}，写入本节点 id</li>
 *   <li>按节点 type 写入 {@code entryKind}（http / subflow / scenario）</li>
 * </ul>
 * 不改 flow / out；步骤恒为通过，图继续沿出边前进。
 * 画布侧入口配置面板可另做；本 Handler 保证引擎能跑通带入口的图。
 */
@Component
public class EntryNodeHandler implements NodeHandler {

    /** 仅处理三种入口节点 type */
    @Override
    public boolean supports(String type) {
        return FlowNodeType.isEntry(type);
    }

    /** 记录入口节点 id 与 entryKind，不改 flow/out，步骤恒通过 */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        if (ctx != null && node != null && node.getId() != null) {
            if (ctx.getEntryNodeId() == null || ctx.getEntryNodeId().isBlank()) {
                ctx.setEntryNodeId(node.getId());
            }
            EntryKind kind = EntryKind.forEntryNodeType(node.getType());
            if (kind != null) {
                ctx.setEntryKind(kind.getCode());
            }
        }
        String name = AbstractStubNodeHandler.resolveNodeName(node);
        return StepResult.builder()
                .nodeId(node != null ? node.getId() : null)
                .nodeType(node != null ? node.getType() : null)
                .nodeName(name)
                .edgeId(incomingEdgeId)
                .status(RunStatus.PASSED.getCode())
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .flowAfter(AbstractStubNodeHandler.copyFlow(ctx))
                .build();
    }
}
