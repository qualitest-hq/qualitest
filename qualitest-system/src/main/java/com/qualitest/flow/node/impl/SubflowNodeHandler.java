package com.qualitest.flow.node.impl;


import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.RunTrigger;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.subflow.SubflowIoSupport;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流节点执行器（type=subflow）。
 * <p>
 * 将同项目内另一张测试流图折叠为单步执行：
 * <ol>
 *   <li>按 {@code subflowId} 加载测试流（须与当前 Run 同 {@code testProjectId}）</li>
 *   <li>子图经 {@link FlowRunReadinessGate} 就绪检查；未通过则本步失败，不进子 runner</li>
 *   <li>解析 inputs：同时写入子上下文 {@code in} 与 {@code flow}；子 Run 的 entryKind/trigger 均为 subflow</li>
 *   <li>用 {@link FlowGraphRunner} 跑子图；子图 {@code subflow-return} 或收束时把定稿 out 挂到父图
 *       {@code nodes.&lt;本 subflow 节点 id&gt;.out}，供父图占位符读取</li>
 *   <li>本步成败只看子 Outcome；是否已挂 out 不影响成败判定；子失败时父图仍可读已挂的 out</li>
 *   <li>子图经 {@link ParsedGraphCache} 取被引用流的当前定义</li>
 * </ol>
 */
@Component
public class SubflowNodeHandler extends AbstractStubNodeHandler {

    private final ITestFlowService testFlowService;
    private final ParsedGraphCache parsedGraphCache;
    private final FlowGraphRunner flowGraphRunner;
    private final FlowRunReadinessGate flowRunReadinessGate;
    private final NodeHandlerRegistry nodeHandlerRegistry;

    public SubflowNodeHandler(ITestFlowService testFlowService,
                              ParsedGraphCache parsedGraphCache,
                              FlowGraphRunner flowGraphRunner,
                              FlowRunReadinessGate flowRunReadinessGate,
                              @Lazy NodeHandlerRegistry nodeHandlerRegistry) {
        super(FlowNodeType.SUBFLOW);
        this.testFlowService = testFlowService;
        this.parsedGraphCache = parsedGraphCache;
        this.flowGraphRunner = flowGraphRunner;
        this.flowRunReadinessGate = flowRunReadinessGate;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String nodeName = resolveNodeName(node);
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();

        try {
            if (!SubflowDepth.canInvokeSubflow(ctx.getSubflowDepth())) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_NESTED,
                        "子流嵌套超过上限（最多 " + SubflowDepth.MAX_DEPTH + " 层）",
                        data, null, List.of());
            }

            Long subflowId = parseSubflowId(data.get("subflowId"));
            TestFlow subflow = testFlowService.selectTestFlowById(subflowId);
            if (subflow == null || (subflow.getDelStatus() != null && subflow.getDelStatus() != 0)) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流不存在: " + subflowId, data, subflowId, List.of());
            }
            if (ctx.getTestProjectId() != null && subflow.getTestProjectId() != null
                    && !ctx.getTestProjectId().equals(subflow.getTestProjectId())) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流不属于当前项目", data, subflowId, List.of());
            }

            GraphJson childGraph = resolveSubflowGraph(subflow);
            if (childGraph == null) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流图解析失败", data, subflowId, List.of());
            }

            List<String> readinessErrors = flowRunReadinessGate.collectBlockingErrors(
                    childGraph, ctx.getTestProjectId());
            if (readinessErrors != null && !readinessErrors.isEmpty()) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_GRAPH_INVALID,
                        String.join("; ", readinessErrors),
                        data, subflowId, List.of());
            }

            Map<String, Object> childSeed = SubflowIoSupport.resolveInputSeed(ctx, data.get("inputs"));
            FlowRunContext childCtx = buildChildContext(ctx, childSeed, node.getId());

            FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                    childGraph, childCtx, nodeHandlerRegistry);
            List<Map<String, Object>> childSteps = SubflowIoSupport.toChildStepSummaries(outcome.getSteps());
            long durationMs = System.currentTimeMillis() - t0;

            Map<String, Object> mounted = ctx.getNodeOut(node.getId());
            Map<String, Object> subflowDetails = buildSubflowDetails(
                    subflowId, subflow.getFlowName(), outcome.isPassed() ? "passed" : "failed", childSteps);
            if (mounted != null) {
                subflowDetails.put("out", new LinkedHashMap<>(mounted));
            }

            if (!outcome.isPassed()) {
                StepError childError = outcome.getError() != null
                        ? outcome.getError()
                        : StepError.of(FlowErrorCode.TF_STEP_ERROR, "子流执行失败");
                return StepResult.builder()
                        .nodeId(node.getId())
                        .nodeType(FlowNodeType.SUBFLOW.getCode())
                        .nodeName(nodeName)
                        .edgeId(incomingEdgeId)
                        .status(RunStatus.FAILED.getCode())
                        .durationMs(durationMs)
                        .subflow(subflowDetails)
                        .flowAfter(copyFlow(ctx))
                        .error(childError)
                        .build();
            }

            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.SUBFLOW.getCode())
                    .nodeName(nodeName)
                    .edgeId(incomingEdgeId)
                    .status(RunStatus.PASSED.getCode())
                    .durationMs(durationMs)
                    .subflow(subflowDetails)
                    .flowAfter(copyFlow(ctx))
                    .build();
        } catch (FlowExecutionException e) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx, e.getErrorCode(), e.getMessage(),
                    data, null, List.of());
        } catch (Exception e) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    FlowErrorCode.TF_STEP_ERROR,
                    e.getMessage() != null ? e.getMessage() : "子流步骤异常",
                    data, null, List.of());
        }
    }

    /**
     * 组装子流运行上下文。
     * 继承父级 env / asset / session / 外联权限 / 项目鉴权配置；inputs 种子同时灌入 in 与 flow。
     * 记录 parentContext 与 parentSubflowNodeId，供子图交付时把 out 挂回父图对应节点。
     */
    private static FlowRunContext buildChildContext(FlowRunContext parent, Map<String, Object> childSeed,
                                                    String parentSubflowNodeId) {
        Map<String, Object> seed = childSeed != null ? new HashMap<>(childSeed) : new HashMap<>();
        return FlowRunContext.builder()
                .testProjectId(parent.getTestProjectId())
                .projectAuthConfig(parent.getProjectAuthConfig())
                .env(parent.getEnv() != null ? parent.getEnv() : Map.of())
                .asset(parent.getAsset() != null ? parent.getAsset() : Map.of())
                .session(parent.getSession() != null ? parent.getSession() : new HashMap<>())
                .subflowDepth(parent.getSubflowDepth() + 1)
                .externalHttpPermitted(parent.isExternalHttpPermitted())
                .flow(new HashMap<>(seed))
                .in(new HashMap<>(seed))
                .entryKind(EntryKind.SUBFLOW.getCode())
                .trigger(RunTrigger.SUBFLOW.getCode())
                .parentContext(parent)
                .parentSubflowNodeId(parentSubflowNodeId)
                .build();
    }

    /**
     * 取被引用流当前图：优先 {@link ParsedGraphCache}，避免同 Run 内重复 parse。
     */
    private GraphJson resolveSubflowGraph(TestFlow subflow) {
        GraphJson cached = parsedGraphCache.getFrom(subflow);
        if (cached != null) {
            return cached;
        }
        if (subflow.getGraphJson() == null || subflow.getGraphJson().isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "子流 graph_json 为空");
        }
        return null;
    }

    private static Map<String, Object> buildSubflowDetails(
            Long subflowId, String subflowName, String status, List<Map<String, Object>> childSteps) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subflowId", String.valueOf(subflowId));
        details.put("subflowName", subflowName != null ? subflowName : "");
        details.put("status", status);
        details.put("childSteps", childSteps);
        return details;
    }

    private static StepResult failed(GraphNode node, String incomingEdgeId, String nodeName, long t0,
                                     FlowRunContext ctx, FlowErrorCode code, String message,
                                     Map<String, Object> data, Long subflowId,
                                     List<Map<String, Object>> childSteps) {
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);
        Map<String, Object> subflowDetails = null;
        if (subflowId != null) {
            subflowDetails = buildSubflowDetails(subflowId, null, "failed", childSteps);
        }
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.SUBFLOW.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.FAILED.getCode())
                .durationMs(durationMs)
                .subflow(subflowDetails)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(code, message))
                .build();
    }

    private static Long parseSubflowId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "未配置 subflowId");
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "subflowId 无效");
        }
    }
}
