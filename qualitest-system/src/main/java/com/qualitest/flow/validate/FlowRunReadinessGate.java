package com.qualitest.flow.validate;

import com.qualitest.flow.cache.ParsedGraphCache;
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
 * 开跑就绪原料收集器。
 * <p>
 * 只产出尚未按项目流检查配置定级的原始问题：完整图结构、路由占用冲突、
 * 断言/条件路径错误、登录键碰撞、HTTP 必填测值。
 * 定级、过滤与硬拦文案统一由 {@link FlowGraphValidateService} 完成。
 */
@Component
@RequiredArgsConstructor
public class FlowRunReadinessGate {

    private final GraphJsonValidator graphJsonValidator;
    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;
    private final ParsedGraphCache parsedGraphCache;
    private final OpenHttpRouteRegistry openHttpRouteRegistry;

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

        issues.addAll(collectAssertPathIssues(graph, effective));
        issues.addAll(collectAuthIssues(graph, testProjectId, effective));
        return issues;
    }

    /**
     * 仅断言 / 条件路径设计错误（画布与开跑共用）。
     */
    public List<FlowIssue> collectAssertPathIssues(GraphJson graph, FlowIssue.Stage stage) {
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        List<FlowIssue> issues = new ArrayList<>();
        if (graph == null) {
            return issues;
        }
        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        for (String err : AssertPathDesignGate.validate(graph, apiResolver).errors()) {
            issues.add(FlowIssue.error(FlowIssueCodes.ASSERT_PATH, effective, null, err));
        }
        return issues;
    }

    /**
     * 鉴权碰撞与 HTTP 必填相关问题（画布与开跑共用）。
     * 鉴权与必填分不同 code；不再做「图上是否已写出鉴权头路径」硬拦。
     */
    public List<FlowIssue> collectAuthIssues(GraphJson graph, Long testProjectId, FlowIssue.Stage stage) {
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        List<FlowIssue> issues = new ArrayList<>();
        if (graph == null) {
            return issues;
        }
        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        for (String err : DesignAuthRiskGates.collectAuthOnly(graph, projectAuthJson, apiResolver)) {
            issues.add(FlowIssue.error(FlowIssueCodes.RUN_READINESS_AUTH, effective, null, err));
        }
        for (String err : DesignAuthRiskGates.collectHttpRequiredOnly(graph, apiResolver)) {
            issues.add(FlowIssue.error(FlowIssueCodes.RUN_READINESS_HTTP_PARAM, effective, null, err));
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