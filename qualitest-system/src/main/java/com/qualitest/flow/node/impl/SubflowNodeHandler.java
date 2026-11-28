package com.qualitest.flow.node.impl;


import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.context.EntryRunPolicySupport;
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
import com.qualitest.flow.run.FlowNodePreExecuteHook;
import com.qualitest.flow.run.RunContinuation;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.RunTrigger;
import com.qualitest.flow.delivery.SettleSupport;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.subflow.SubflowIoSupport;
import com.qualitest.flow.validate.FlowGraphValidateService;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流节点执行器（type=subflow）。
 * <p>
 * 将同项目内另一张测试流图折叠为单步执行：
 * <ol>
 *   <li>按 {@code subflowId} 加载测试流（须与当前 Run 同 {@code testProjectId}）</li>
 *   <li>子图经 {@link FlowGraphValidateService} 开跑类校验；未通过则本步失败，不进子 runner</li>
 *   <li>解析 inputs：写入子上下文 {@code in}；子 Run 的 entryKind/trigger 均为 subflow；
 *       通过 {@link FlowRunContext#forkForSubflow} 继承父的环境/运行 id、外联与暂停许可及共享区；
 *       可选 {@code entryNodeId} 指定进哪个子流入口</li>
 *   <li>用 {@link FlowGraphRunner} 跑子图（步数上限取子流入口 {@code maxSteps}）；
 *       子图 {@code subflow-return} 或收束时把定稿 out 挂到父图
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
    private final FlowGraphValidateService flowGraphValidateService;
    private final NodeHandlerRegistry nodeHandlerRegistry;

    public SubflowNodeHandler(ITestFlowService testFlowService,
                              ParsedGraphCache parsedGraphCache,
                              FlowGraphRunner flowGraphRunner,
                              FlowGraphValidateService flowGraphValidateService,
                              @Lazy NodeHandlerRegistry nodeHandlerRegistry) {
        super(FlowNodeType.SUBFLOW);
        this.testFlowService = testFlowService;
        this.parsedGraphCache = parsedGraphCache;
        this.flowGraphRunner = flowGraphRunner;
        this.flowGraphValidateService = flowGraphValidateService;
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
                ctx.putNodeOut(node.getId(), SettleSupport.defaultSubflowFallbackPackage());
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流不存在: " + subflowId, data, subflowId, List.of());
            }
            if (ctx.getTestProjectId() != null && subflow.getTestProjectId() != null
                    && !ctx.getTestProjectId().equals(subflow.getTestProjectId())) {
                ctx.putNodeOut(node.getId(), SettleSupport.defaultSubflowFallbackPackage());
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流不属于当前项目", data, subflowId, List.of());
            }

            GraphJson childGraph = resolveSubflowGraph(subflow);
            if (childGraph == null) {
                // 子图不可用也要给父一个包，避免父侧读 nodes.<id>.out 时凭空为空
                ctx.putNodeOut(node.getId(), SettleSupport.defaultSubflowFallbackPackage());
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流图解析失败", data, subflowId, List.of());
            }

            Map<String, Object> childSeed = SubflowIoSupport.resolveInputSeed(ctx, data.get("inputs"));
            String childEntryNodeId = resolveChildEntryNodeId(data.get("entryNodeId"), childGraph);
            // 同一场 Run：子图继承身份与 pause/外联许可，避免子流里丢环境或改严改松权限
            FlowRunContext childCtx = FlowRunContext.forkForSubflow(
                    ctx,
                    childSeed,
                    node.getId(),
                    childEntryNodeId,
                    EntryKind.SUBFLOW.getCode(),
                    RunTrigger.SUBFLOW.getCode());

            List<String> readinessErrors = flowGraphValidateService.collectBlockingErrorMessages(
                    childGraph,
                    FlowIssue.Stage.SUBFLOW_CALL,
                    ctx.getTestProjectId(),
                    subflowId,
                    ctx.getTestProjectEnvId());
            if (readinessErrors != null && !readinessErrors.isEmpty()) {
                // 开跑被拦仍按子流入口 fallbackOut 挂父：调用方要「有返回」
                ctx.putNodeOut(node.getId(), SettleSupport.resolveSubflowFallbackPackage(childCtx, childGraph));
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_GRAPH_INVALID,
                        String.join("; ", readinessErrors),
                        data, subflowId, List.of());
            }

            RunSnapshotPolicy childPolicy = EntryRunPolicySupport.compose(
                    childGraph, childEntryNodeId, null);

            FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                    childGraph, childCtx, nodeHandlerRegistry,
                    FlowNodePreExecuteHook.NONE, RunContinuation.fresh(), childPolicy);
            List<Map<String, Object>> childSteps = SubflowIoSupport.toChildStepSummaries(outcome.getSteps());
            long durationMs = System.currentTimeMillis() - t0;

            Map<String, Object> mounted = ctx.getNodeOut(node.getId());
            Map<String, Object> subflowDetails = buildSubflowDetails(
                    subflowId, subflow.getFlowName(), outcome.isPassed() ? "passed" : "failed", childSteps);
            if (mounted != null) {
                subflowDetails.put("out", new LinkedHashMap<>(mounted));
            }

            if (!outcome.isPassed()) {
                ensureFallbackOutMounted(ctx, node.getId());
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
            ensureFallbackOutMounted(ctx, node.getId());
            return failed(node, incomingEdgeId, nodeName, t0, ctx, e.getErrorCode(), e.getMessage(),
                    data, null, List.of());
        } catch (Exception e) {
            ensureFallbackOutMounted(ctx, node.getId());
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    FlowErrorCode.TF_STEP_ERROR,
                    e.getMessage() != null ? e.getMessage() : "子流步骤异常",
                    data, null, List.of());
        }
    }

    /** 子流被拦/失败且父侧还没有 out 时，补一份默认兜底包，保证父图读 nodes.<id>.out 有值 */
    private static void ensureFallbackOutMounted(FlowRunContext ctx, String nodeId) {
        if (ctx != null && nodeId != null && ctx.getNodeOut(nodeId) == null) {
            ctx.putNodeOut(nodeId, SettleSupport.defaultSubflowFallbackPackage());
        }
    }

    /**
     * 解析父节点指定的子流入口 id（委托 {@link com.qualitest.flow.contract.EntryLocator}）。
     */
    private static String resolveChildEntryNodeId(Object rawEntryNodeId, GraphJson childGraph) {
        String specified = rawEntryNodeId == null ? null : String.valueOf(rawEntryNodeId).trim();
        if (specified != null && specified.isEmpty()) {
            specified = null;
        }
        return com.qualitest.flow.contract.EntryLocator.requireSubflowEntryId(childGraph, specified);
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
