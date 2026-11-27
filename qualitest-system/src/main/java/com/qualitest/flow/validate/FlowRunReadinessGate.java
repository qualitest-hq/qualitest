package com.qualitest.flow.validate;

import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.inspect.FlowIssueFinalizeService;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 正式开跑前的就绪检查。
 * <p>
 * 依次检查：完整图结构、路由占用冲突、断言/条件路径错误、鉴权凭证来源、登录抽取、HTTP 必填测值。
 * 保存图时不走本门禁。对外硬拦只认定级后的 ERROR；项目流检查配置可把部分码降为 WARNING 或关闭。
 */
@Component
@RequiredArgsConstructor
public class FlowRunReadinessGate {

    private final GraphJsonValidator graphJsonValidator;
    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;
    private final ParsedGraphCache parsedGraphCache;
    private final OpenHttpRouteRegistry openHttpRouteRegistry;
    private final FlowIssueFinalizeService flowIssueFinalizeService;

    /**
     * 收集阻止开跑的全部错误文案。
     * 未传当前流 id 时仍可做跨流路由冲突检查。
     *
     * @param graph         已解析的图
     * @param testProjectId 所属测试项目，用于读鉴权配置与路由冲突对照
     * @return 错误文案列表；空表示可以开跑
     */
    public List<String> collectBlockingErrors(GraphJson graph, Long testProjectId) {
        return collectBlockingErrors(graph, testProjectId, null);
    }

    /**
     * 收集阻止开跑的全部错误文案。
     *
     * @param graph         已解析的图
     * @param testProjectId 所属测试项目，用于读鉴权配置与路由冲突对照
     * @param testFlowId    当前流 id（可空）；用于排除自身已保存的路由
     * @return 错误文案列表；空表示可以开跑
     */
    public List<String> collectBlockingErrors(GraphJson graph, Long testProjectId, Long testFlowId) {
        List<String> errors = new ArrayList<>();
        for (FlowIssue issue : collectBlockingIssues(graph, testProjectId, testFlowId, FlowIssue.Stage.RUN)) {
            if (issue != null && issue.isError() && issue.getMessage() != null) {
                errors.add(issue.getMessage());
            }
        }
        return errors;
    }

    /**
     * 收集会硬拦开跑的结构化问题。
     * 先汇总原始就绪问题，再按项目流检查配置定级，只保留 ERROR。
     */
    public List<FlowIssue> collectBlockingIssues(
            GraphJson graph, Long testProjectId, Long testFlowId, FlowIssue.Stage stage) {
        List<FlowIssue> raw = collectRawBlockingIssues(graph, testProjectId, testFlowId, stage);
        List<FlowIssue> finalized = flowIssueFinalizeService.finalizeIssues(raw, testProjectId);
        List<FlowIssue> blocking = new ArrayList<>();
        for (FlowIssue issue : finalized) {
            if (issue != null && issue.isError()) {
                blocking.add(issue);
            }
        }
        return blocking;
    }

    /**
     * 收集开跑相关的原始问题（尚未按项目流检查配置定级）。
     * 供统一校验服务在合并 API 语义、插件检查后再一起定级。
     */
    public List<FlowIssue> collectRawBlockingIssues(
            GraphJson graph, Long testProjectId, Long testFlowId, FlowIssue.Stage stage) {
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.RUN;
        List<FlowIssue> issues = new ArrayList<>();
        if (graph == null) {
            issues.add(FlowIssue.error(
                    FlowIssueCodes.GRAPH_JSON_INVALID, effective, null, "根对象必须是 JSON 对象"));
            return issues;
        }
        GraphValidationResult graphResult = graphJsonValidator.validate(graph, GraphValidationOptions.full());
        issues.addAll(FlowIssues.fromValidationResult(graphResult, effective));

        if (testProjectId != null) {
            issues.addAll(openHttpRouteRegistry.conflicts(graph, testProjectId, testFlowId, effective));
        }

        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        for (String err : AssertPathDesignGate.validate(graph, apiResolver).errors()) {
            issues.add(FlowIssue.error(FlowIssueCodes.ASSERT_PATH, effective, null, err));
        }

        issues.addAll(collectAuthIssues(graph, testProjectId, effective));
        return issues;
    }

    /**
     * 仅鉴权、登录抽取、HTTP 必填相关问题（画布与开跑共用）。
     * 不跑结构、路由、断言路径检查。
     */
    public List<FlowIssue> collectAuthIssues(GraphJson graph, Long testProjectId, FlowIssue.Stage stage) {
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        List<FlowIssue> issues = new ArrayList<>();
        if (graph == null) {
            return issues;
        }
        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        Function<Long, GraphJson> subflowResolver = parsedGraphCache::get;
        for (String err : DesignAuthRiskGates.collect(graph, projectAuthJson, apiResolver, subflowResolver)) {
            issues.add(FlowIssue.error(FlowIssueCodes.RUN_READINESS_AUTH, effective, null, err));
        }
        return issues;
    }

    /** 读取项目鉴权配置 JSON；无项目或不存在时返回 null */
    private String loadProjectAuthConfig(Long testProjectId) {
        if (testProjectId == null) {
            return null;
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        return project != null ? project.getAuthConfig() : null;
    }
}
