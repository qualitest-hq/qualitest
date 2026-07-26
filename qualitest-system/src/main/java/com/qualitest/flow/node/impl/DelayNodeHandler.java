package com.qualitest.flow.node.impl;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Delay 节点执行器：按 {@code data.ms} 阻塞当前线程，记录实际耗时。
 */
@Component
public class DelayNodeHandler extends AbstractStubNodeHandler {

    /** 单步最大等待毫秒数，防止配置过大拖垮执行线程 */
    private static final long MAX_DELAY_MS = 60_000L;

    public DelayNodeHandler() {
        super(FlowNodeType.DELAY);
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        long ms = resolveDelayMs(data.get("ms"));

        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.DELAY.getCode())
                    .nodeName(resolveNodeName(node))
                    .edgeId(incomingEdgeId)
                    .status(StepResult.STATUS_FAILED)
                    .durationMs(System.currentTimeMillis() - t0)
                    .flowAfter(copyFlow(ctx))
                    .error(StepError.of(FlowErrorCode.TF_STEP_ERROR, "Delay 被中断"))
                    .build();
        }

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.DELAY.getCode())
                .nodeName(resolveNodeName(node))
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_PASSED)
                .durationMs(System.currentTimeMillis() - t0)
                .flowAfter(copyFlow(ctx))
                .build();
    }

    private static long resolveDelayMs(Object raw) {
        long ms = 1000L;
        if (raw instanceof Number n) {
            ms = n.longValue();
        } else if (raw != null) {
            try {
                ms = Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                ms = 1000L;
            }
        }
        if (ms < 0) {
            ms = 0;
        }
        if (ms > MAX_DELAY_MS) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR, "Delay 超过上限 " + MAX_DELAY_MS + "ms");
        }
        return ms;
    }
}
