package com.qualitest.flow.node.impl;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.migrate.GraphMigrator;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.subflow.GraphMetaIoSupport;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.subflow.SubflowIoSupport;
import com.qualitest.flow.validate.FlowNodeType;
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
 * 将同项目内另一张测试流图折叠为单步执行，对外通过 inputs/outputs 映射变量：
 * <ol>
 *   <li>按 {@code subflowId} 加载测试流（须同 {@code testProjectId}）</li>
 *   <li>{@link SubflowIoSupport#resolveInputSeed} 解析 inputs 占位符，写入子 {@link com.qualitest.flow.context.FlowRunContext} 的 flow</li>
 *   <li>{@link FlowGraphRunner} 在内存跑子图（fail-fast）；嵌套深度由 {@link SubflowDepth} 限制（主→子→孙，最多 2 层 subflow 嵌套）</li>
 *   <li>{@link SubflowIoSupport#mergeOutputsToParent} 将子 flow 按 outputs 或子图 {@code meta.flowOutputs} 默认映射写回父 flow</li>
 *   <li>步骤 {@code subflow.childSteps} 附带内层步骤摘要，供 Run 详情展开与 AI {@code get_run_failure} 分析</li>
 * </ol>
 * {@code versionPolicy}：{@code latest} 每次 Run 取子流当前图；{@code pinned} 优先用节点 {@code pinnedGraphJson} 固化快照。
 * <p>
 * pinned 子图快照不参与父流 schema 升级写回；运行期仅在内存中 migrate 以保证可执行，不改变父节点固化内容。
 */
@Component
public class SubflowNodeHandler extends AbstractStubNodeHandler {

    private static final String VERSION_PINNED = "pinned";
    private static final String VERSION_LATEST = "latest";

    private final ITestFlowService testFlowService;
    private final FlowGraphRunner flowGraphRunner;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final GraphMigrator graphMigrator;

    public SubflowNodeHandler(ITestFlowService testFlowService,
                              FlowGraphRunner flowGraphRunner,
                              @Lazy NodeHandlerRegistry nodeHandlerRegistry,
                              GraphMigrator graphMigrator) {
        super(FlowNodeType.SUBFLOW);
        this.testFlowService = testFlowService;
        this.flowGraphRunner = flowGraphRunner;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
        this.graphMigrator = graphMigrator;
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

            String graphJson = resolveSubflowGraphJson(data, subflow);
            GraphJson childGraph = GraphJson.parse(graphJson);
            if (childGraph == null) {
                return failed(node, incomingEdgeId, nodeName, t0, ctx,
                        FlowErrorCode.TF_SUBFLOW_INVALID, "子流图解析失败", data, subflowId, List.of());
            }
            if (graphMigrator.needsUpgrade(childGraph)) {
                childGraph = graphMigrator.migrateToLatest(childGraph);
            }

            Map<String, Object> childSeed = SubflowIoSupport.resolveInputSeed(ctx, data.get("inputs"));
            FlowRunContext childCtx = buildChildContext(ctx, childSeed);

            FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                    childGraph, childCtx, nodeHandlerRegistry);
            List<Map<String, Object>> childSteps = SubflowIoSupport.toChildStepSummaries(outcome.getSteps());
            long durationMs = System.currentTimeMillis() - t0;

            Map<String, Object> subflowDetails = buildSubflowDetails(
                    subflowId, subflow.getFlowName(), outcome.isPassed() ? "passed" : "failed", childSteps);

            if (!outcome.isPassed()) {
                StepError childError = outcome.getError() != null
                        ? outcome.getError()
                        : StepError.of(FlowErrorCode.TF_STEP_ERROR, "子流执行失败");
                return StepResult.builder()
                        .nodeId(node.getId())
                        .nodeType(FlowNodeType.SUBFLOW.getCode())
                        .nodeName(nodeName)
                        .edgeId(incomingEdgeId)
                        .status(StepResult.STATUS_FAILED)
                        .durationMs(durationMs)
                        .subflow(subflowDetails)
                        .flowAfter(copyFlow(ctx))
                        .error(childError)
                        .build();
            }

            Object outputsObj = data.get("outputs");
            if (SubflowIoSupport.isEmptyMappingList(outputsObj)) {
                outputsObj = GraphMetaIoSupport.defaultOutputMappings(childGraph.getMeta());
            }
            Map<String, Object> mergedOutputs = SubflowIoSupport.applyOutputs(
                    ctx, childCtx.getFlow(), outputsObj);
            subflowDetails.put("outputsMerged", mergedOutputs);

            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.SUBFLOW.getCode())
                    .nodeName(nodeName)
                    .edgeId(incomingEdgeId)
                    .status(StepResult.STATUS_PASSED)
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

    /** 子上下文继承父级的 env/asset/session/外联权限/响应约定/鉴权配置；flow 仅保留 inputs 种子。
     * 继承后，子流内 HTTP 节点仍按同一套业务码与鉴权头规则处理。 */
    private static FlowRunContext buildChildContext(FlowRunContext parent, Map<String, Object> childSeed) {
        return FlowRunContext.builder()
                .testProjectId(parent.getTestProjectId())
                .responseConvention(parent.getResponseConvention())
                .projectAuthConfig(parent.getProjectAuthConfig())
                .env(parent.getEnv() != null ? parent.getEnv() : Map.of())
                .asset(parent.getAsset() != null ? parent.getAsset() : Map.of())
                .session(parent.getSession() != null ? parent.getSession() : new HashMap<>())
                .subflowDepth(parent.getSubflowDepth() + 1)
                .externalHttpPermitted(parent.isExternalHttpPermitted())
                .flow(new HashMap<>(childSeed))
                .build();
    }

    /** 按 versionPolicy 选择子图 JSON：pinned 用节点快照，否则读子流定义当前 graph_json */
    private static String resolveSubflowGraphJson(Map<String, Object> data, TestFlow subflow) {
        String policy = data.get("versionPolicy") != null
                ? String.valueOf(data.get("versionPolicy")).trim()
                : VERSION_LATEST;
        if (VERSION_PINNED.equalsIgnoreCase(policy)) {
            Object pinned = data.get("pinnedGraphJson");
            if (pinned != null && !String.valueOf(pinned).isBlank()) {
                return String.valueOf(pinned);
            }
        }
        if (subflow.getGraphJson() == null || subflow.getGraphJson().isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "子流 graph_json 为空");
        }
        return subflow.getGraphJson();
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
                .status(StepResult.STATUS_FAILED)
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
