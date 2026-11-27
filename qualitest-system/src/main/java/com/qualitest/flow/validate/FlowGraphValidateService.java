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
 * 除图结构外，还会合并路由冲突、鉴权就绪、API 语义、平台插件检查与（开跑时）插件 Invoke 检查，
 * 最后按项目流检查配置定级并过滤已关闭项。
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
            return FlowGraphValidateResult.of(finalizeIssues(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID, stage, null, "graphJson 不能为空")), testProjectId));
        }
        return switch (stage) {
            case SAVE -> validateSave(graphJson, testProjectId);
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
    private FlowGraphValidateResult validateSave(String graphJson, Long testProjectId) {
        try {
            GraphJson graph = GraphJson.parse(graphJson);
            if (graph == null) {
                return FlowGraphValidateResult.of(finalizeIssues(List.of(FlowIssue.error(
                        FlowIssueCodes.GRAPH_JSON_INVALID,
                        FlowIssue.Stage.SAVE,
                        null,
                        "graph_json 无法解析：解析结果为空")), testProjectId));
            }
            return FlowGraphValidateResult.of(List.of());
        } catch (Exception e) {
            return FlowGraphValidateResult.of(finalizeIssues(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID,
                    FlowIssue.Stage.SAVE,
                    null,
                    "graph_json 无法解析：" + e.getMessage())), testProjectId));
        }
    }

    /**
     * 画布阶段：结构校验 + 路由冲突 + 鉴权就绪 + 平台插件检查 + API 语义。
     * 图解析失败时只保留结构类问题；其它收集器在图可用时再跑，异常向上抛出。
     */
    private FlowGraphValidateResult validateCanvas(
            String graphJson, Long testProjectId, Long testFlowId, boolean deferTopology) {
        GraphValidationOptions options = deferTopology
                ? GraphValidationOptions.deferTopology()
                : GraphValidationOptions.full();
        GraphValidationResult graphResult = graphJsonValidator.validateJson(graphJson, options);
        List<FlowIssue> issues = new ArrayList<>(
                FlowIssues.fromValidationResult(graphResult, FlowIssue.Stage.CANVAS));
        GraphJson graph = tryParseGraph(graphJson);
        if (graph != null) {
            if (testProjectId != null) {
                issues.addAll(openHttpRouteRegistry.conflicts(
                        graph, testProjectId, testFlowId, FlowIssue.Stage.CANVAS));
                issues.addAll(flowRunReadinessGate.collectAuthIssues(
                        graph, testProjectId, FlowIssue.Stage.CANVAS));
                issues.addAll(platformPluginInspectionEvaluator.evaluate(
                        graph, testProjectId, FlowIssue.Stage.CANVAS));
            }
            issues.addAll(apiHealthIssueCollector.collect(graphJson, FlowIssue.Stage.CANVAS));
        }
        return FlowGraphValidateResult.of(finalizeIssues(dedupe(issues), testProjectId));
    }

    /**
     * 尝试解析图对象。
     * 失败返回 null；结构错误已由 {@link GraphJsonValidator#validateJson} 收集，此处不再抛出。
     */
    private static GraphJson tryParseGraph(String graphJson) {
        try {
            return GraphJson.parse(graphJson);
        } catch (RuntimeException e) {
            return null;
        }
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
        GraphJson graph;
        try {
            graph = GraphJson.parse(graphJson);
        } catch (Exception e) {
            return FlowGraphValidateResult.of(finalizeIssues(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID,
                    stage,
                    null,
                    "graph_json 无法解析：" + e.getMessage())), testProjectId));
        }
        if (graph == null) {
            return FlowGraphValidateResult.of(finalizeIssues(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID, stage, null, "graph_json 无法解析：解析结果为空")),
                    testProjectId));
        }
        List<FlowIssue> issues = new ArrayList<>(
                flowRunReadinessGate.collectRawBlockingIssues(graph, testProjectId, testFlowId, stage));
        issues.addAll(apiHealthIssueCollector.collect(graphJson, stage));
        issues.addAll(platformPluginInspectionEvaluator.evaluate(graph, testProjectId, stage));
        issues.addAll(pluginInvokeInspectionCollector.collect(
                testProjectEnvId, testProjectId, testFlowId, graphJson, stage));
        return FlowGraphValidateResult.of(finalizeIssues(dedupe(issues), testProjectId));
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
