package com.qualitest.flow.node.impl;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

/**
 * Input 节点执行器。
 * <p>
 * 首次执行只返回 {@code paused}，不修改 flow；图遍历器据此将 Run 置为
 * {@code await_input} 暂停。人工提交后由续跑路径校验写入并合成 passed 步，本 Handler 不再执行。
 */
@Component
public class InputNodeHandler extends AbstractStubNodeHandler {

    public InputNodeHandler() {
        super(FlowNodeType.INPUT);
    }

    /**
     * 返回暂停步：status=paused，错误码 TF_AWAIT_INPUT（软暂停，非业务失败）。
     */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.INPUT.getCode())
                .nodeName(resolveNodeName(node))
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_PAUSED)
                .durationMs(0L)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(FlowErrorCode.TF_AWAIT_INPUT, "等待人工输入"))
                .build();
    }
}
