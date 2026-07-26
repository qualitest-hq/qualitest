package com.qualitest.ai.tools;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.flow.FlowGraphContextResolver;
import com.qualitest.ai.tools.flow.GetApiDetailTool;
import com.qualitest.ai.tools.flow.GetFlowApiHealthTool;
import com.qualitest.ai.tools.flow.GetFlowMetaTool;
import com.qualitest.ai.tools.flow.GetFlowTool;
import com.qualitest.ai.tools.flow.GetGraphSummaryTool;
import com.qualitest.ai.tools.flow.GetNodeDetailTool;
import com.qualitest.ai.tools.flow.GetRunFailureTool;
import com.qualitest.ai.tools.flow.ListFlowsTool;
import com.qualitest.ai.tools.flow.ListProjectEnvsTool;
import com.qualitest.ai.tools.flow.GetSubflowDetailTool;
import com.qualitest.ai.tools.flow.ListSubflowTemplatesTool;
import com.qualitest.ai.tools.flow.SearchApisTool;
import com.qualitest.ai.tools.flow.SubmitFlowDesignPatchTool;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流 AI 设计 Function Calling 统一执行器。
 * <p>
 * Web Agent 与 MCP 网关共用本类调度 {@link QualitestTool} 实现：
 * <ul>
 *   <li><b>项目资产只读</b> — search_apis、get_api_detail、list_project_envs</li>
 *   <li><b>画布/测试流只读</b> — get_graph_summary、get_flow_meta、get_node_detail、get_run_failure、
 *       get_flow_api_health（检查 HTTP 节点 API 语义告警）、list_subflow_templates、get_subflow_detail；
 *       MCP 额外提供 list_flows、get_flow</li>
 *   <li><b>Web 写入建议</b> — submit_flow_design_patch：经 {@link FlowDesignPatchNormalizer} 校验，
 *       结果供前端 Diff 合并，不写库（MCP 拒绝调用）</li>
 * </ul>
 * 工具返回 JSON 字符串；顶层 {@code error} 字段表示业务失败（Web/MCP 共用此约定）。
 * 结果超限时附 {@code truncated} 与 {@code hint}。
 */
@Slf4j
@Component
public class FlowDesignToolExecutor {

    public static final String SEARCH_APIS = FlowDesignToolNames.SEARCH_APIS.getId();
    public static final String GET_API_DETAIL = FlowDesignToolNames.GET_API_DETAIL.getId();
    public static final String GET_GRAPH_SUMMARY = FlowDesignToolNames.GET_GRAPH_SUMMARY.getId();
    public static final String GET_FLOW_META = FlowDesignToolNames.GET_FLOW_META.getId();
    public static final String LIST_PROJECT_ENVS = FlowDesignToolNames.LIST_PROJECT_ENVS.getId();
    public static final String GET_NODE_DETAIL = FlowDesignToolNames.GET_NODE_DETAIL.getId();
    public static final String GET_RUN_FAILURE = FlowDesignToolNames.GET_RUN_FAILURE.getId();
    /**
     * 工具名：检查当前画布 HTTP 节点的 API 语义健康告警
     * （接口缺失、孤儿测值、抽取路径失效等），不写库。
     */
    public static final String GET_FLOW_API_HEALTH = FlowDesignToolNames.GET_FLOW_API_HEALTH.getId();
    /** 平台模板 + 项目测试流摘要，供 subflow 节点选型 */
    public static final String LIST_SUBFLOW_TEMPLATES = FlowDesignToolNames.LIST_SUBFLOW_TEMPLATES.getId();
    /** 被引用测试流的拓扑与 flowOutputs 摘要 */
    public static final String GET_SUBFLOW_DETAIL = FlowDesignToolNames.GET_SUBFLOW_DETAIL.getId();
    public static final String LIST_FLOWS = FlowDesignToolNames.LIST_FLOWS.getId();
    public static final String GET_FLOW = FlowDesignToolNames.GET_FLOW.getId();
    public static final String SUBMIT_FLOW_DESIGN_PATCH = FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId();

    private final Map<String, QualitestTool> tools;

    public FlowDesignToolExecutor(TestProjectApiMapper testProjectApiMapper,
                                  TestProjectMapper testProjectMapper,
                                  ITestProjectEnvService testProjectEnvService,
                                  ITestFlowService testFlowService,
                                  ITestFlowRunService testFlowRunService,
                                  ITestFlowRunStepService testFlowRunStepService,
                                  FlowDesignPatchNormalizer flowDesignPatchNormalizer,
                                  HttpNodeApiHealthChecker httpNodeApiHealthChecker) {
        FlowGraphContextResolver graphResolver = new FlowGraphContextResolver(testFlowService);
        Map<String, QualitestTool> map = new HashMap<>();
        map.put(SEARCH_APIS, new SearchApisTool(testProjectApiMapper));
        map.put(GET_API_DETAIL, new GetApiDetailTool(testProjectApiMapper, testProjectMapper));
        map.put(LIST_FLOWS, new ListFlowsTool(testFlowService));
        map.put(LIST_SUBFLOW_TEMPLATES, new ListSubflowTemplatesTool(testFlowService));
        map.put(GET_SUBFLOW_DETAIL, new GetSubflowDetailTool(testFlowService));
        map.put(GET_FLOW, new GetFlowTool(testFlowService));
        map.put(GET_GRAPH_SUMMARY, new GetGraphSummaryTool(graphResolver));
        map.put(GET_FLOW_META, new GetFlowMetaTool(graphResolver));
        map.put(LIST_PROJECT_ENVS, new ListProjectEnvsTool(testProjectEnvService));
        map.put(GET_NODE_DETAIL, new GetNodeDetailTool(graphResolver));
        map.put(GET_RUN_FAILURE, new GetRunFailureTool(testFlowRunService, testFlowRunStepService));
        // 语义健康：优先用注入的检查器，单测未注入时 new 一个默认实例
        map.put(GET_FLOW_API_HEALTH, new GetFlowApiHealthTool(
                graphResolver,
                httpNodeApiHealthChecker != null ? httpNodeApiHealthChecker : new HttpNodeApiHealthChecker(),
                testProjectApiMapper));
        map.put(SUBMIT_FLOW_DESIGN_PATCH, new SubmitFlowDesignPatchTool(flowDesignPatchNormalizer));
        this.tools = Map.copyOf(map);
    }

    /** 已注册工具名集合，供启动期一致性校验使用。 */
    public Set<String> registeredToolNames() {
        return tools.keySet();
    }

    /** enum 中声明的全部工具 id。 */
    public static Set<String> allDeclaredToolNames() {
        return java.util.Arrays.stream(FlowDesignToolNames.values())
                .map(FlowDesignToolNames::getId)
                .collect(Collectors.toSet());
    }

    /** 判断工具名是否允许经 MCP 网关调用 */
    public static boolean isMcpAllowedTool(String name) {
        return FlowDesignToolNames.isMcpAllowed(name);
    }

    /**
     * 按工具名执行一次调用，返回 JSON 字符串（成功为数据对象，失败含 error 字段）。
     */
    public String executeTool(String name, String argumentsJson, FlowDesignToolContext context) {
        QualitestTool tool = tools.get(name);
        if (tool == null) {
            return FlowDesignToolSupport.errorJson("未知工具: " + name);
        }
        Map<String, Object> args;
        try {
            args = FlowDesignToolSupport.parseArgs(argumentsJson);
        } catch (IllegalArgumentException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        }
        try {
            return tool.execute(args, context);
        } catch (Exception ex) {
            log.warn("工具 {} 执行异常: {}", name, ex.getMessage(), ex);
            return FlowDesignToolSupport.errorJson("工具执行异常: " + ex.getMessage());
        }
    }
}
