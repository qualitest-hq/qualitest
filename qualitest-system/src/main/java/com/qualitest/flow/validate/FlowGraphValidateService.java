package com.qualitest.flow.validate;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.inspect.ApiHealthIssueCollector;
import com.qualitest.flow.inspect.FlowIssueFinalizeService;
import com.qualitest.flow.inspect.PlatformPluginInspectionEvaluator;
import com.qualitest.flow.inspect.PluginInvokeInspectionCollector;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按阶段产出统一流问题列表。
 * 除图结构外，还会合并路由冲突、断言路径、鉴权就绪、API 语义、平台插件检查与（开跑时）插件 Invoke 检查，
 * 最后按项目流检查配置定级并过滤已关闭项。
 * {@link FlowIssueCodes#GRAPH_JSON_INVALID} 为解析地板：短路返回，不经流检查定级。
 */
@Service
@RequiredArgsConstructor
public class FlowGraphValidateService {

    private final GraphJsonValidator graphJsonValidator;
    private final FlowRunReadinessGate flowRunReadinessGate;
    private final OpenHttpRouteRegistry openHttpRouteRegistry;
    private final ApiHealthIssueCollector apiHealthIssueCollector;
    private final PlatformPluginInspectionEvaluator platformPluginInspectionEvaluator;
    private final PluginInvokeInspectionCollector pluginInvokeInspectionCollector;
    private final FlowIssueFinalizeService flowIssueFinalizeService;

    /**
     * 按阶段校验图。
     *
     * @param graphJson        图 JSON 文本
     * @param stage            阶段：画布 / 保存 / 开跑 / 入口 / 子流调用
     * @param testProjectId    项目 id；路由、鉴权、插件检查需要；可空
     * @param testFlowId       当前流 id；可空
     * @param deferTopology    仅画布：有未确认草稿边时推迟部分拓扑硬拦
     * @param testProjectEnvId 仅开跑类阶段：环境 id，用于调度插件流检查
     */
    public FlowGraphValidateResult validate(
            String graphJson,
            FlowIssue.Stage stage,
            Long testProjectId,
            Long testFlowId,
            boolean deferTopology,
            Long testProjectEnvId) {
        if (stage == null) {
            throw new ServiceException("stage 不能为空（canvas|save|run|ingress|subflow）");
        }
        if (graphJson == null || graphJson.isBlank()) {
            return parseFloorResult(stage, "graphJson 不能为空");
        }
        return switch (stage) {
            case SAVE -> validateSave(graphJson);
            case CANVAS -> validateCanvas(graphJson, testProjectId, testFlowId, deferTopology);
            case RUN, INGRESS, SUBFLOW_CALL ->
                    validateRunLike(graphJson, stage, testProjectId, testFlowId, testProjectEnvId);
        };
    }

    /** 旧调用入口：画布不推迟拓扑、不带环境 */
    public FlowGraphValidateResult validate(
            String graphJson, FlowIssue.Stage stage, Long testProjectId, Long testFlowId) {
        return validate(graphJson, stage, testProjectId, testFlowId, false, null);
    }

    /** 带拓扑推迟开关、不带环境的入口 */
    public FlowGraphValidateResult validate(
            String graphJson,
            FlowIssue.Stage stage,
            Long testProjectId,
            Long testFlowId,
            boolean deferTopology) {
        return validate(graphJson, stage, testProjectId, testFlowId, deferTopology, null);
    }

    /**
     * 保存阶段：只要求图能解析成对象。
     * 结构错误、路由冲突等不在此拦截，便于草稿落盘。
     */
    private FlowGraphValidateResult validateSave(String graphJson) {
        ParseOutcome parsed = parseGraph(graphJson, FlowIssue.Stage.SAVE);
        if (parsed.floorFailure() != null) {
            return parsed.floorFailure();
        }
        return FlowGraphValidateResult.of(List.of());
    }

    /**
     * 画布阶段：结构校验 + 路由冲突 + 断言路径 + 鉴权就绪 + 平台插件检查 + API 语义。
     * 图文本无法解析时短路为 {@link FlowIssueCodes#GRAPH_JSON_INVALID}（与 save/run 同口径）。
     */
    private FlowGraphValidateResult validateCanvas(
            String graphJson, Long testProjectId, Long testFlowId, boolean deferTopology) {
        ParseOutcome parsed = parseGraph(graphJson, FlowIssue.Stage.CANVAS);
        if (parsed.floorFailure() != null) {
            return parsed.floorFailure();
        }
        GraphJson graph = parsed.graph();
        GraphValidationOptions options = deferTopology
                ? GraphValidationOptions.deferTopology()
                : GraphValidationOptions.full();
        // raw 校验保留未知键 / 边多余字段；解析地板已在上方短路
        GraphValidationResult graphResult = graphJsonValidator.validateJson(graphJson, options);
        List<FlowIssue> issues = new ArrayList<>(
                FlowIssues.fromValidationResult(graphResult, FlowIssue.Stage.CANVAS));
        issues.addAll(flowRunReadinessGate.collectAssertPathIssues(graph, FlowIssue.Stage.CANVAS));
        if (testProjectId != null) {
            issues.addAll(openHttpRouteRegistry.conflicts(
                    graph, testProjectId, testFlowId, FlowIssue.Stage.CANVAS));
            issues.addAll(flowRunReadinessGate.collectAuthIssues(
                    graph, testProjectId, FlowIssue.Stage.CANVAS));
            issues.addAll(platformPluginInspectionEvaluator.evaluate(
                    graph, testProjectId, FlowIssue.Stage.CANVAS));
        }
        issues.addAll(apiHealthIssueCollector.collect(graphJson, FlowIssue.Stage.CANVAS));
        return FlowGraphValidateResult.of(finalizeIssues(dedupe(issues), testProjectId));
    }

    /**
     * 开跑及同类阶段：完整就绪问题 + API 语义 + 平台插件检查 + 环境插件 Invoke 检查。
     * 图无法解析时只返回 JSON 无效问题。
     */
    private FlowGraphValidateResult validateRunLike(
            String graphJson,
            FlowIssue.Stage stage,
            Long testProjectId,
            Long testFlowId,
            Long testProjectEnvId) {
        ParseOutcome parsed = parseGraph(graphJson, stage);
        if (parsed.floorFailure() != null) {
            return parsed.floorFailure();
        }
        GraphJson graph = parsed.graph();
        List<FlowIssue> issues = new ArrayList<>(
                flowRunReadinessGate.collectRawBlockingIssues(graph, testProjectId, testFlowId, stage));
        issues.addAll(apiHealthIssueCollector.collect(graphJson, stage));
        issues.addAll(platformPluginInspectionEvaluator.evaluate(graph, testProjectId, stage));
        issues.addAll(pluginInvokeInspectionCollector.collect(
                testProjectEnvId, testProjectId, testFlowId, graphJson, stage));
        return FlowGraphValidateResult.of(finalizeIssues(dedupe(issues), testProjectId));
    }

    /** 解析成功带图；失败带 GRAPH_JSON_INVALID 结果（二选一）。 */
    private record ParseOutcome(GraphJson graph, FlowGraphValidateResult floorFailure) {}

    /** 统一解析地板：save / canvas / run-like 共用，避免三段重复 try/catch。 */
    private static ParseOutcome parseGraph(String graphJson, FlowIssue.Stage stage) {
        try {
            GraphJson graph = GraphJson.parse(graphJson);
            if (graph == null) {
                return new ParseOutcome(null, parseFloorResult(stage, "graph_json 无法解析：解析结果为空"));
            }
            return new ParseOutcome(graph, null);
        } catch (Exception e) {
            return new ParseOutcome(null, parseFloorResult(stage, "graph_json 无法解析：" + e.getMessage()));
        }
    }

    /**
     * 收集某阶段定级后的 ERROR 文案（开跑 / 入站 / 子流硬拦用）。
     * 定级与过滤只走本服务，不再由就绪门禁自行 finalize。
     */
    public List<String> collectBlockingErrorMessages(
            GraphJson graph,
            FlowIssue.Stage stage,
            Long testProjectId,
            Long testFlowId,
            Long testProjectEnvId) {
        if (graph == null) {
            return List.of("根对象必须是 JSON 对象");
        }
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.RUN;
        FlowGraphValidateResult result = validate(
                graph.toJsonString(), effective, testProjectId, testFlowId, false, testProjectEnvId);
        List<String> errors = new ArrayList<>();
        for (FlowIssue issue : result.getIssues()) {
            if (issue != null && issue.isError() && issue.getMessage() != null) {
                errors.add(issue.getMessage());
            }
        }
        return errors;
    }

    /**
     * 解析地板失败：固定 ERROR，不经流检查定级（不能被 inspection_profile OFF）。
     */
    private static FlowGraphValidateResult parseFloorResult(FlowIssue.Stage stage, String message) {
        return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                FlowIssueCodes.GRAPH_JSON_INVALID, stage, null, message)));
    }

    /** 按项目流检查配置定级并过滤 OFF */
    private List<FlowIssue> finalizeIssues(List<FlowIssue> raw, Long testProjectId) {
        return flowIssueFinalizeService.finalizeIssues(raw, testProjectId);
    }

    /** 按码、严重性、节点、文案去重，保留首次出现顺序 */
    private static List<FlowIssue> dedupe(List<FlowIssue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<FlowIssue> out = new ArrayList<>(issues.size());
        for (FlowIssue issue : issues) {
            String key = issue.getCode() + '|'
                    + issue.getSeverity() + '|'
                    + issue.getNodeId() + '|'
                    + issue.getMessage();
            if (seen.add(key)) {
                out.add(issue);
            }
        }
        return out;
    }
}
