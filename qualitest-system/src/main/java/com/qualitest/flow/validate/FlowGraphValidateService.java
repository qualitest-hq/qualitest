package com.qualitest.flow.validate;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按 stage 产出统一 FlowIssue 列表（P1 校验收口；P2 将升格为 FlowGate）。
 */
@Service
@RequiredArgsConstructor
public class FlowGraphValidateService {

    private final GraphJsonValidator graphJsonValidator;
    private final FlowRunReadinessGate flowRunReadinessGate;
    private final OpenHttpRouteRegistry openHttpRouteRegistry;

    /**
     * 校验入参。
     *
     * @param graphJson      图 JSON 文本或对象序列化文本
     * @param stage          canvas / save / run / ingress / subflow
     * @param testProjectId  项目 id（路由与鉴权需要；可空）
     * @param testFlowId     当前流 id（可空）
     * @param deferTopology  仅 canvas：有未确认 Staging 边时延后开始节点等拓扑硬拦
     */
    public FlowGraphValidateResult validate(
            String graphJson,
            FlowIssue.Stage stage,
            Long testProjectId,
            Long testFlowId,
            boolean deferTopology) {
        if (stage == null) {
            throw new ServiceException("stage 不能为空（canvas|save|run|ingress|subflow）");
        }
        if (graphJson == null || graphJson.isBlank()) {
            return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID, stage, null, "graphJson 不能为空")));
        }
        return switch (stage) {
            case SAVE -> validateSave(graphJson);
            case CANVAS -> validateCanvas(graphJson, testProjectId, testFlowId, deferTopology);
            case RUN, INGRESS, SUBFLOW_CALL -> validateRunLike(graphJson, stage, testProjectId, testFlowId);
        };
    }

    /** 兼容旧调用：canvas 不延后拓扑 */
    public FlowGraphValidateResult validate(
            String graphJson, FlowIssue.Stage stage, Long testProjectId, Long testFlowId) {
        return validate(graphJson, stage, testProjectId, testFlowId, false);
    }

    private FlowGraphValidateResult validateSave(String graphJson) {
        try {
            GraphJson graph = GraphJson.parse(graphJson);
            if (graph == null) {
                return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                        FlowIssueCodes.GRAPH_JSON_INVALID,
                        FlowIssue.Stage.SAVE,
                        null,
                        "graph_json 无法解析：解析结果为空")));
            }
            return FlowGraphValidateResult.of(List.of());
        } catch (Exception e) {
            return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID,
                    FlowIssue.Stage.SAVE,
                    null,
                    "graph_json 无法解析：" + e.getMessage())));
        }
    }

    private FlowGraphValidateResult validateCanvas(
            String graphJson, Long testProjectId, Long testFlowId, boolean deferTopology) {
        GraphValidationOptions options = deferTopology
                ? GraphValidationOptions.deferTopology()
                : GraphValidationOptions.full();
        // 走 raw 路径，保留未知键 / 边多余字段等 typed 会丢掉的问题
        GraphValidationResult structure = graphJsonValidator.validateJson(graphJson, options);
        List<FlowIssue> issues = new ArrayList<>(
                FlowIssues.fromValidationResult(structure, FlowIssue.Stage.CANVAS));
        try {
            GraphJson graph = GraphJson.parse(graphJson);
            if (graph != null && testProjectId != null) {
                issues.addAll(openHttpRouteRegistry.conflicts(
                        graph, testProjectId, testFlowId, FlowIssue.Stage.CANVAS));
                // 鉴权/必填并进画布图 error（不硬拦保存；开跑仍由 readiness 硬拦）
                issues.addAll(flowRunReadinessGate.collectAuthIssues(
                        graph, testProjectId, FlowIssue.Stage.CANVAS));
            }
        } catch (Exception ignored) {
            // 结构错误已在 validateJson 收集
        }
        return FlowGraphValidateResult.of(dedupe(issues));
    }

    private FlowGraphValidateResult validateRunLike(
            String graphJson, FlowIssue.Stage stage, Long testProjectId, Long testFlowId) {
        GraphJson graph;
        try {
            graph = GraphJson.parse(graphJson);
        } catch (Exception e) {
            return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID,
                    stage,
                    null,
                    "graph_json 无法解析：" + e.getMessage())));
        }
        if (graph == null) {
            return FlowGraphValidateResult.of(List.of(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID, stage, null, "graph_json 无法解析：解析结果为空")));
        }
        return FlowGraphValidateResult.of(dedupe(
                flowRunReadinessGate.collectBlockingIssues(graph, testProjectId, testFlowId, stage)));
    }

    private static List<FlowIssue> dedupe(List<FlowIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<FlowIssue> out = new ArrayList<>();
        for (FlowIssue issue : issues) {
            if (issue == null) {
                continue;
            }
            String key = String.valueOf(issue.getCode()) + '|'
                    + String.valueOf(issue.getSeverity()) + '|'
                    + String.valueOf(issue.getNodeId()) + '|'
                    + String.valueOf(issue.getMessage());
            if (seen.add(key)) {
                out.add(issue);
            }
        }
        return out;
    }
}
