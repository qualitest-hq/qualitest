package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.model.FlowNodeType;
import org.springframework.stereotype.Component;

/**
 * Input 节点执行器。
 * <p>
 * 首次执行只返回 {@code paused}，不修改 flow；图遍历器据此将 Run 置为
 * {@code await_input} 暂停。人工提交后由续跑路径校验写入并合成 passed 步，本 Handler 不再执行。
 * <p>
 * 开放 HTTP 一旦已交付响应，就无法再暂停续跑：此时返回失败步并说明原因，避免留下
 * 「响应已发出、Run 却挂在暂停态但不可恢复」的矛盾。
 */
@Component
public class InputNodeHandler extends AbstractStubNodeHandler {

    public InputNodeHandler() {
        super(FlowNodeType.INPUT);
    }

    /**
     * 返回暂停步：status=paused，错误码 TF_AWAIT_INPUT（软暂停，非业务失败）；
     * 开放 HTTP 已交付时改为失败步。
     */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        if (ctx != null && ctx.isDelivered()
                && EntryKind.fromCode(ctx.getEntryKind()) == EntryKind.HTTP) {
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.INPUT.getCode())
                    .nodeName(resolveNodeName(node))
                    .edgeId(incomingEdgeId)
                    .status(RunStatus.FAILED.getCode())
                    .durationMs(0L)
                    .flowAfter(copyFlow(ctx))
                    .error(StepError.of(FlowErrorCode.TF_AWAIT_INPUT,
                            "响应已交付后遇到人工输入，开放入站不再续跑"))
                    .build();
        }
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.INPUT.getCode())
                .nodeName(resolveNodeName(node))
                .edgeId(incomingEdgeId)
                .status(RunStatus.PAUSED.getCode())
                .durationMs(0L)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(FlowErrorCode.TF_AWAIT_INPUT, "等待人工输入"))
                .build();
    }
}
