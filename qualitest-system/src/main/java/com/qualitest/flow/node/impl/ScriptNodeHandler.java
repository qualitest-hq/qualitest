package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.script.ScriptConstants;
import com.qualitest.flow.script.ScriptExecutionResult;
import com.qualitest.flow.script.ScriptRuntime;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Script 节点执行器。
 * <p>
 * 读取 {@code data.language}、{@code data.source}、{@code data.timeoutMs}，
 * 在 GraalVM 沙箱中执行脚本；{@code ctx.setFlow} 写入合并进当前 Run 的 flow，
 * 步骤详情写入 {@code StepResult.script}。
 */
@Component
public class ScriptNodeHandler extends AbstractStubNodeHandler {

    private final ScriptRuntime scriptRuntime;

    public ScriptNodeHandler(ScriptRuntime scriptRuntime) {
        super(FlowNodeType.SCRIPT);
        this.scriptRuntime = scriptRuntime;
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String nodeName = resolveNodeName(node);
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();

        String language = data.get("language") != null
                ? String.valueOf(data.get("language")).trim()
                : ScriptConstants.LANGUAGE_JAVASCRIPT;
        String source = data.get("source") != null ? String.valueOf(data.get("source")) : "";
        long timeoutMs = ScriptConstants.normalizeTimeoutMs(data.get("timeoutMs"));

        ScriptExecutionResult result = scriptRuntime.execute(language, source, timeoutMs, ctx);
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);

        if (!result.isSuccess()) {
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.SCRIPT.getCode())
                    .nodeName(nodeName)
                    .edgeId(incomingEdgeId)
                    .status(RunStatus.FAILED.getCode())
                    .durationMs(durationMs)
                    .flowAfter(copyFlow(ctx))
                    .script(result.toStepScriptDetails(language))
                    .error(StepError.of(result.getErrorCode(), result.getErrorMessage()))
                    .build();
        }

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.SCRIPT.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.PASSED.getCode())
                .durationMs(durationMs)
                .flowAfter(copyFlow(ctx))
                .script(result.toStepScriptDetails(language))
                .build();
    }
}
