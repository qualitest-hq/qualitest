package com.qualitest.ai.tools;

import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.scenario.flow.FlowDesignPatchMerger;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.flow.AppendApiDesignHintsTool;
import com.qualitest.ai.tools.flow.FlowDesignUnitSubmitSupport;
import com.qualitest.ai.tools.flow.FlowGraphContextResolver;
import com.qualitest.ai.tools.flow.GetApiDetailsTool;
import com.qualitest.ai.tools.flow.GetEdgeDetailTool;
import com.qualitest.ai.tools.flow.GetFlowApiHealthTool;
import com.qualitest.ai.tools.flow.GetFlowMetaTool;
import com.qualitest.ai.tools.flow.GetFlowTool;
import com.qualitest.ai.tools.flow.GetGraphSummaryTool;
import com.qualitest.ai.tools.flow.GetNodeDetailTool;
import com.qualitest.ai.tools.flow.GetRunFailureTool;
import com.qualitest.ai.tools.flow.GetScenarioDetailTool;
import com.qualitest.ai.tools.flow.ListAssetVariablesTool;
import com.qualitest.ai.tools.flow.ListFlowsTool;
import com.qualitest.ai.tools.flow.ListProjectEnvsTool;
import com.qualitest.ai.tools.flow.GetSubflowDetailTool;
import com.qualitest.ai.tools.flow.ListSubflowTemplatesTool;
import com.qualitest.ai.tools.flow.SearchApisTool;
import com.qualitest.ai.tools.flow.SubmitFlowDesignUnitTool;
import com.qualitest.ai.tools.flow.UpsertAssetVariablesTool;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectAssetService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流 AI 设计 Function Calling 统一执行器。
 * <p>
 * 构造时注册全部只读工具与 submit_* 写图单元工具，按工具名路由执行。
 * Web Agent 与 MCP 共用本执行器；MCP 调用入口另行拦截 submit_*，禁止改图。
 */
@Slf4j
@Component
public class FlowDesignToolExecutor {

    public static final String SEARCH_APIS = FlowDesignToolNames.SEARCH_APIS.getId();
    public static final String GET_API_DETAILS = FlowDesignToolNames.GET_API_DETAILS.getId();
    public static final String GET_GRAPH_SUMMARY = FlowDesignToolNames.GET_GRAPH_SUMMARY.getId();
    public static final String GET_FLOW_META = FlowDesignToolNames.GET_FLOW_META.getId();
    public static final String LIST_PROJECT_ENVS = FlowDesignToolNames.LIST_PROJECT_ENVS.getId();
    public static final String LIST_ASSET_VARIABLES = FlowDesignToolNames.LIST_ASSET_VARIABLES.getId();
    public static final String UPSERT_ASSET_VARIABLES = FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();
    public static final String APPEND_API_DESIGN_HINTS = FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId();
    public static final String GET_NODE_DETAIL = FlowDesignToolNames.GET_NODE_DETAIL.getId();
    public static final String GET_EDGE_DETAIL = FlowDesignToolNames.GET_EDGE_DETAIL.getId();
    public static final String GET_SCENARIO_DETAIL = FlowDesignToolNames.GET_SCENARIO_DETAIL.getId();
    public static final String GET_RUN_FAILURE = FlowDesignToolNames.GET_RUN_FAILURE.getId();
    public static final String GET_FLOW_API_HEALTH = FlowDesignToolNames.GET_FLOW_API_HEALTH.getId();
    public static final String LIST_SUBFLOW_TEMPLATES = FlowDesignToolNames.LIST_SUBFLOW_TEMPLATES.getId();
    public static final String GET_SUBFLOW_DETAIL = FlowDesignToolNames.GET_SUBFLOW_DETAIL.getId();
    public static final String LIST_FLOWS = FlowDesignToolNames.LIST_FLOWS.getId();
    public static final String GET_FLOW = FlowDesignToolNames.GET_FLOW.getId();

    private final Map<String, QualitestTool> tools;

    public FlowDesignToolExecutor(TestProjectApiMapper testProjectApiMapper,
                                  TestProjectMapper testProjectMapper,
                                  ITestProjectEnvService testProjectEnvService,
                                  ITestFlowService testFlowService,
                                  ITestFlowRunService testFlowRunService,
                                  ITestFlowRunStepService testFlowRunStepService,
                                  FlowDesignPatchNormalizer flowDesignPatchNormalizer,
                                  FlowDesignPatchMerger flowDesignPatchMerger,
                                  HttpNodeApiHealthChecker httpNodeApiHealthChecker,
                                  ITestProjectAssetService testProjectAssetService,
                                  TestProjectApiDesignHintsService designHintsService,
                                  AiChatConversationService aiChatConversationService) {
        FlowGraphContextResolver graphResolver = new FlowGraphContextResolver(testFlowService);
        FlowDesignUnitSubmitSupport unitSubmit = new FlowDesignUnitSubmitSupport(
                flowDesignPatchNormalizer, aiChatConversationService, flowDesignPatchMerger);
        Map<String, QualitestTool> map = new HashMap<>();
        map.put(SEARCH_APIS, new SearchApisTool(testProjectApiMapper, testProjectMapper));
        map.put(GET_API_DETAILS, new GetApiDetailsTool(testProjectApiMapper, testProjectMapper));
        map.put(LIST_FLOWS, new ListFlowsTool(testFlowService));
        map.put(LIST_SUBFLOW_TEMPLATES, new ListSubflowTemplatesTool(testFlowService));
        map.put(GET_SUBFLOW_DETAIL, new GetSubflowDetailTool(testFlowService));
        map.put(GET_FLOW, new GetFlowTool(testFlowService));
        map.put(GET_GRAPH_SUMMARY, new GetGraphSummaryTool(graphResolver));
        map.put(GET_FLOW_META, new GetFlowMetaTool(graphResolver));
        map.put(LIST_PROJECT_ENVS, new ListProjectEnvsTool(testProjectEnvService));
        map.put(LIST_ASSET_VARIABLES, new ListAssetVariablesTool(testProjectMapper));
        map.put(UPSERT_ASSET_VARIABLES, new UpsertAssetVariablesTool(testProjectAssetService));
        map.put(APPEND_API_DESIGN_HINTS, new AppendApiDesignHintsTool(testProjectApiMapper, designHintsService));
        map.put(GET_NODE_DETAIL, new GetNodeDetailTool(graphResolver));
        map.put(GET_EDGE_DETAIL, new GetEdgeDetailTool(graphResolver));
        map.put(GET_SCENARIO_DETAIL, new GetScenarioDetailTool(graphResolver));
        map.put(GET_RUN_FAILURE, new GetRunFailureTool(testFlowRunService, testFlowRunStepService));
        map.put(GET_FLOW_API_HEALTH, new GetFlowApiHealthTool(
                graphResolver,
                httpNodeApiHealthChecker != null ? httpNodeApiHealthChecker : new HttpNodeApiHealthChecker(),
                testProjectApiMapper));
        registerSubmitUnitTools(map, unitSubmit);
        this.tools = Map.copyOf(map);
    }

    /**
     * 注册全部 submit_* 写图工具：
     * 七种节点与边、场景各用 upsert（参数 op=add|update）；删除统一 submit_delete。
     * 节点工具把 type 闭包进 builder，参数里写错类型也以注册类型为准。
     */
    private static void registerSubmitUnitTools(Map<String, QualitestTool> map,
                                                FlowDesignUnitSubmitSupport unitSubmit) {
        record NodeTool(FlowDesignToolNames name, String type) {}
        NodeTool[] nodeTools = {
                new NodeTool(FlowDesignToolNames.SUBMIT_HTTP_NODE, "http"),
                new NodeTool(FlowDesignToolNames.SUBMIT_ASSERT_NODE, "assert"),
                new NodeTool(FlowDesignToolNames.SUBMIT_CONDITION_NODE, "condition"),
                new NodeTool(FlowDesignToolNames.SUBMIT_ASSIGN_NODE, "assign"),
                new NodeTool(FlowDesignToolNames.SUBMIT_DELAY_NODE, "delay"),
                new NodeTool(FlowDesignToolNames.SUBMIT_SCRIPT_NODE, "script"),
                new NodeTool(FlowDesignToolNames.SUBMIT_SUBFLOW_NODE, "subflow"),
        };
        for (NodeTool nt : nodeTools) {
            String type = nt.type();
            putUnit(map, nt.name(), a -> SubmitFlowDesignUnitTool.upsertNode(type, a), unitSubmit);
        }
        putUnit(map, FlowDesignToolNames.SUBMIT_EDGE, SubmitFlowDesignUnitTool::upsertEdge, unitSubmit);
        putUnit(map, FlowDesignToolNames.SUBMIT_SCENARIO, SubmitFlowDesignUnitTool::upsertScenario, unitSubmit);
        putUnit(map, FlowDesignToolNames.SUBMIT_DELETE, SubmitFlowDesignUnitTool::delete, unitSubmit);
    }

    /** 把「工具枚举名 + 参数→单单元 patch 的构建器」注册为可执行工具。 */
    private static void putUnit(Map<String, QualitestTool> map,
                                FlowDesignToolNames name,
                                java.util.function.Function<Map<String, Object>, FlowDesignPatch> builder,
                                FlowDesignUnitSubmitSupport unitSubmit) {
        map.put(name.getId(), new SubmitFlowDesignUnitTool(name.getId(), builder, unitSubmit));
    }

    /** 当前执行器已注册的工具名（启动校验用）。 */
    public Set<String> registeredToolNames() {
        return tools.keySet();
    }

    /** 枚举声明的全部工具名（含 Web 与仅 MCP）。 */
    public static Set<String> allDeclaredToolNames() {
        return java.util.Arrays.stream(FlowDesignToolNames.values())
                .map(FlowDesignToolNames::getId)
                .collect(Collectors.toSet());
    }

    /** 是否允许 MCP 调用该工具名。 */
    public static boolean isMcpAllowedTool(String name) {
        return FlowDesignToolNames.isMcpAllowed(name);
    }

    /**
     * 按名执行工具；argumentsJson 为模型传来的参数对象字符串。
     * 未知工具或参数解析失败返回 error JSON，不抛给 Agent 循环外。
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
