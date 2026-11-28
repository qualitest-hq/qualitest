package com.qualitest.flow.node.impl;


import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.input.AwaitInputHookSupport;
import com.qualitest.flow.input.InputFieldTypes;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Input 节点执行器（type=input）。
 * <p>
 * 执行顺序：
 * <ol>
 *   <li>开放 HTTP 且已对外交付过：直接 failed（入站不可再等人续跑）</li>
 *   <li>问 {@code onAwaitInput} 挂点能否自动填表；成功则校验写入 flow，本步 passed</li>
 *   <li>自动填失败或无挂点：若 {@code pausePermitted} 为 false → failed；否则 → paused（等待填表）</li>
 * </ol>
 * 暂停后由用户提交字段值续跑（continueWithInput），或中止（abort）。
 */
@Component
public class InputNodeHandler extends AbstractStubNodeHandler {

    private final AwaitInputHookSupport awaitInputHookSupport;

    public InputNodeHandler(AwaitInputHookSupport awaitInputHookSupport) {
        super(FlowNodeType.INPUT);
        this.awaitInputHookSupport = awaitInputHookSupport;
    }

    /**
     * 自动填表成功 → passed；允许暂停 → paused；否则 failed。
     */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        String nodeName = resolveNodeName(node);
        // 开放入站已写出响应后不得再等人；否则客户端已拿到包，续跑无意义
        if (ctx.isDelivered() && EntryKind.fromCode(ctx.getEntryKind()) == EntryKind.HTTP) {
            return finish(node, nodeName, incomingEdgeId, ctx, RunStatus.FAILED,
                    "响应已交付后遇到人工输入，开放入站不再续跑");
        }

        Optional<Map<String, Object>> auto = awaitInputHookSupport.tryAutoFill(ctx, node);
        if (auto.isPresent()) {
            try {
                Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
                List<Map<String, Object>> fields = InputFieldTypes.parseFields(data.get("fields"));
                if (ctx.getFlow() == null) {
                    ctx.setFlow(new HashMap<>());
                }
                InputFieldTypes.validateAndApply(fields, auto.get(), ctx.getFlow());
                return finish(node, nodeName, incomingEdgeId, ctx, RunStatus.PASSED, null);
            } catch (Exception e) {
                // 自动填表校验失败：不中断，回退到暂停或失败
            }
        }

        // 项目关暂停或入站强制禁止暂停时，不能落 paused
        if (!ctx.isPausePermitted()) {
            return finish(node, nodeName, incomingEdgeId, ctx, RunStatus.FAILED,
                    "项目未允许暂停，输入节点无法等待填表");
        }

        return finish(node, nodeName, incomingEdgeId, ctx, RunStatus.PAUSED, "等待人工输入");
    }

    /**
     * 组装本步结果；errorMessage 非空时附带 TF_AWAIT_INPUT 错误信息（paused / failed 均使用）。
     */
    private static StepResult finish(GraphNode node, String nodeName, String edgeId,
                                     FlowRunContext ctx, RunStatus status, String errorMessage) {
        StepResult.StepResultBuilder builder = StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.INPUT.getCode())
                .nodeName(nodeName)
                .edgeId(edgeId)
                .status(status.getCode())
                .durationMs(0L)
                .flowAfter(copyFlow(ctx));
        if (errorMessage != null) {
            builder.error(StepError.of(FlowErrorCode.TF_AWAIT_INPUT, errorMessage));
        }
        return builder.build();
    }
}
