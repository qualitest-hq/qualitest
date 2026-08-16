package com.qualitest.ai.tools;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.flow.AppendApiDesignHintsTool;
import com.qualitest.ai.tools.flow.FlowGraphContextResolver;
import com.qualitest.ai.tools.flow.GetApiDetailsTool;
import com.qualitest.ai.tools.flow.GetFlowApiHealthTool;
import com.qualitest.ai.tools.flow.GetFlowMetaTool;
import com.qualitest.ai.tools.flow.GetFlowTool;
import com.qualitest.ai.tools.flow.GetGraphSummaryTool;
import com.qualitest.ai.tools.flow.GetNodeDetailTool;
import com.qualitest.ai.tools.flow.GetRunFailureTool;
import com.qualitest.ai.tools.flow.ListAssetVariablesTool;
import com.qualitest.ai.tools.flow.ListFlowsTool;
import com.qualitest.ai.tools.flow.ListProjectEnvsTool;
import com.qualitest.ai.tools.flow.GetSubflowDetailTool;
import com.qualitest.ai.tools.flow.ListSubflowTemplatesTool;
import com.qualitest.ai.tools.flow.SearchApisTool;
import com.qualitest.ai.tools.flow.SubmitFlowDesignPatchTool;
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
 * Web 助手与 MCP 网关都经本类按工具名调度具体实现：
 * <ul>
 *   <li>项目只读：搜接口、读接口详情、列环境、列素材库 key/字段名</li>
 *   <li>素材提案：upsert_asset_variables — 按 key 提出新建/更新素材，待用户确认后写入项目素材库（仅 Web）</li>
 *   <li>画布/流只读：图摘要、场景 meta、节点详情、Run 失败、HTTP 节点 API 健康、子流模板与详情；
 *       MCP 另有按项目列流、读完整流</li>
 *   <li>画布建议写入：submit_flow_design_patch — 校验后返回 patch 供前端 Staging，不直接写库（仅 Web）</li>
 * </ul>
 * 工具返回 JSON 字符串；顶层 error 表示业务失败；超长结果带 truncated 与 hint。
 */
@Slf4j
@Component
public class FlowDesignToolExecutor {

    public static final String SEARCH_APIS = FlowDesignToolNames.SEARCH_APIS.getId();
    public static final String GET_API_DETAILS = FlowDesignToolNames.GET_API_DETAILS.getId();
    /** @deprecated 已删除；调用时返回迁移提示 */
    public static final String GET_API_DETAIL_LEGACY = "get_api_detail";
    public static final String GET_GRAPH_SUMMARY = FlowDesignToolNames.GET_GRAPH_SUMMARY.getId();
    public static final String GET_FLOW_META = FlowDesignToolNames.GET_FLOW_META.getId();
    public static final String LIST_PROJECT_ENVS = FlowDesignToolNames.LIST_PROJECT_ENVS.getId();
    /** 工具名：列举项目素材库 key / 字段名 / 占位提示，不含明文 */
    public static final String LIST_ASSET_VARIABLES = FlowDesignToolNames.LIST_ASSET_VARIABLES.getId();
    /** 工具名：按 key 新增或更新素材并落盘；回执不含明文；仅 Web */
    public static final String UPSERT_ASSET_VARIABLES = FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();
    /** 工具名：向接口 design_hints 追加造流提示；直接落库；仅 Web */
    public static final String APPEND_API_DESIGN_HINTS = FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId();
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
                                  HttpNodeApiHealthChecker httpNodeApiHealthChecker,
                                  ITestProjectAssetService testProjectAssetService,
                                  TestProjectApiDesignHintsService designHintsService) {
        FlowGraphContextResolver graphResolver = new FlowGraphContextResolver(testFlowService);
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
        // 素材库：列举（只读）与按 key 写入（仅 Web）
        map.put(LIST_ASSET_VARIABLES, new ListAssetVariablesTool(testProjectMapper));
        map.put(UPSERT_ASSET_VARIABLES, new UpsertAssetVariablesTool(testProjectAssetService));
        map.put(APPEND_API_DESIGN_HINTS, new AppendApiDesignHintsTool(testProjectApiMapper, designHintsService));
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
        if (GET_API_DETAIL_LEGACY.equals(name)) {
            return FlowDesignToolSupport.errorJson(
                    "工具 get_api_detail 已废弃，请改用 get_api_details（参数 testProjectApiIds 为字符串数组，单条也传 [id]）");
        }
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
