package com.qualitest.flow.node.impl;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.model.FlowNodeType;
import org.springframework.stereotype.Component;

/**
 * 入口节点透传执行器。
 * <p>
 * 支持三种平台入口与协议入口 {@code {protocol}-entry}：
 * <ul>
 *   <li>若上下文尚未记录 {@code entryNodeId}，写入本节点 id</li>
 *   <li>按节点 type 写入 {@code entryKind}（http / subflow / scenario / 协议短名）</li>
 * </ul>
 * 不改 flow / out；步骤恒为通过，图继续沿出边前进。
 */
@Component
public class EntryNodeHandler implements NodeHandler {

    /** 处理三种平台入口与协议入口节点 type */
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
                // 平台入口：直接取对应的入口种类
                ctx.setEntryKind(kind.getCode());
            } else {
                // 协议入口：以协议短名作为入口种类
                String protocol = FlowNodeType.protocolShortName(node.getType());
                if (protocol != null) {
                    ctx.setEntryKind(protocol);
                }
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
