package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.flow.GetApiDetailsTool;
import com.qualitest.api.util.ApiConfigTestFixtures;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestProjectAssetService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignToolExecutor：设计工具只读查询、健康检查与 submit_* 单元工具。
 * 边界：Mock Mapper / Normalizer / 各 Service，不访问 DB 与真实 LLM。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignToolExecutorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignToolExecutorTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long API_ID = 2001L;

    private TestProjectApiMapper mapper;
    private TestProjectMapper projectMapper;
    private ITestProjectEnvService envService;
    private ITestFlowService flowService;
    private ITestFlowRunService runService;
    private ITestFlowRunStepService runStepService;
    private FlowDesignPatchNormalizer normalizer;
    private ITestProjectAssetService assetService;
    private FlowDesignToolExecutor executor;
    private FlowDesignToolContext context;

    @BeforeEach
    void setUp() {
        mapper = mock(TestProjectApiMapper.class);
        projectMapper = mock(TestProjectMapper.class);
        envService = mock(ITestProjectEnvService.class);
        flowService = mock(ITestFlowService.class);
        runService = mock(ITestFlowRunService.class);
        runStepService = mock(ITestFlowRunStepService.class);
        normalizer = mock(FlowDesignPatchNormalizer.class);
        assetService = mock(ITestProjectAssetService.class);
        TestProjectApiDesignHintsService designHintsService = mock(TestProjectApiDesignHintsService.class);
        executor = new FlowDesignToolExecutor(
                mapper, projectMapper, envService, flowService, runService, runStepService,
                mock(com.qualitest.project.service.ITestFlowExecutionService.class),
                normalizer,
                new com.qualitest.ai.scenario.flow.FlowDesignPatchMerger(),
                mock(com.qualitest.flow.validate.GraphJsonValidator.class),
                new HttpNodeApiHealthChecker(), assetService, designHintsService, null,
                mock(com.qualitest.api.service.IApiImportService.class),
                new com.qualitest.ai.mcp.McpPromptResourceService());
        context = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .scopeApiIds(List.of(API_ID))
                .maxSearchApis(5)
                .maxToolResultBytes(8192)
                .build();
    }

    /**
     * 前提：search_apis keyword=login，mapper 返回匹配项。
     * 期望：items 1 条含 id/method/path，truncated=false。
     */
    @Test
    @Order(1)
    @DisplayName("按关键词搜索返回匹配 API")
    void searchApis_byKeyword_returnsMatchedItems() {
        TestProjectApi api = loginApi();
        when(mapper.searchApisByKeyword(PROJECT_ID, "login", 10)).thenReturn(List.of(api));

        String json = executor.executeTool(
                FlowDesignToolExecutor.SEARCH_APIS,
                "{\"keyword\":\"login\"}",
                context);

        JSONObject root = JSON.parseObject(json);
        JSONArray items = root.getJSONArray("items");
        assertEquals(1, items.size());
        assertEquals(String.valueOf(API_ID), items.getJSONObject(0).getString("id"));
        assertEquals("POST", items.getJSONObject(0).getString("method"));
        assertEquals("/api/account/auth/login", items.getJSONObject(0).getString("path"));
        assertFalse(root.getBooleanValue("truncated"));
    }

    /**
     * 前提：无关键词，context.scopeApiIds 已限定范围。
     * 期望：只返回 scope 内 API，不查全库。
     */
    @Test
    @Order(2)
    @DisplayName("无关键词时优先返回 scope API")
    void searchApis_scopeApiIds_priorityWithoutKeyword() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(loginApi());

        String json = executor.executeTool(
                FlowDesignToolExecutor.SEARCH_APIS,
                "{\"keyword\":\"\"}",
                context);

        JSONArray items = JSON.parseObject(json).getJSONArray("items");
        assertEquals(1, items.size());
        assertEquals("用户登录", items.getJSONObject(0).getString("name"));
    }

    /**
     * 前提：get_api_details 传入本项目合法 testProjectApiIds（单元素数组）。
     * 期望：apis[0] 含 method/path/bodyParams（含 username）等语义摘要。
     */
    @Test
    @Order(3)
    @DisplayName("合法 API 返回语义摘要")
    void getApiDetails_validApi_returnsSemanticSummary() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(loginApi());
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(
                com.qualitest.project.domain.TestProject.builder()
                        .testProjectId(PROJECT_ID)
                        .authConfig(com.qualitest.api.util.ProjectAuthConfigSupport.toJson(
                                com.qualitest.api.util.AuthProfileTestFixtures.adminThenClient()))
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAILS,
                "{\"testProjectApiIds\":[\"" + API_ID + "\"]}",
                context);

        JSONObject root = JSON.parseObject(json);
        JSONArray apis = root.getJSONArray("apis");
        assertEquals(1, apis.size());
        JSONObject detail = apis.getJSONObject(0);
        assertEquals(String.valueOf(API_ID), detail.getString("testProjectApiId"));
        assertEquals("POST", detail.getString("method"));
        assertEquals("/api/account/auth/login", detail.getString("path"));
        assertTrue(paramSummariesContainName(detail.getJSONArray("bodyParams"), "username"));
        assertTrue(detail.getString("bodyExample").contains("username"));
        assertNotNull(detail.getJSONObject("responseSchemaSummary"));
        assertNotNull(detail.getJSONObject("responseConvention"));
        assertEquals("code", detail.getJSONObject("responseConvention").getString("codePath"));
        assertNotNull(detail.getJSONArray("suggestedExtracts"));
        assertNotNull(detail.getJSONArray("designHints"));
        assertFalse(root.getBooleanValue("truncated"));
        assertEquals("none", detail.getJSONObject("auth").getString("mode"));
        assertNull(detail.get("headerHint"));
    }

    /**
     * 前提：API 属于其它项目。
     * 期望：apis 空，missingIds 含该 id。
     */
    @Test
    @Order(4)
    @DisplayName("跨项目 API 记入 missingIds")
    void getApiDetails_wrongProject_listedInMissing() {
        TestProjectApi otherProject = loginApi();
        otherProject.setTestProjectId(999L);
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(otherProject);

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAILS,
                "{\"testProjectApiIds\":[\"" + API_ID + "\"]}",
                context);

        JSONObject root = JSON.parseObject(json);
        assertEquals(0, root.getJSONArray("apis").size());
        assertTrue(root.getJSONArray("missingIds").contains(String.valueOf(API_ID)));
    }

    @Test
    @Order(41)
    @DisplayName("批量两个合法 id 返回两条")
    void getApiDetails_twoIds_returnsBoth() {
        Long secondId = 2002L;
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(loginApi());
        TestProjectApi second = loginApi();
        second.setTestProjectApiId(secondId);
        second.setApiPath("/api/cart");
        second.setApiName("购物车");
        when(mapper.selectTestProjectApiById(secondId)).thenReturn(second);
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(
                com.qualitest.project.domain.TestProject.builder().testProjectId(PROJECT_ID).build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAILS,
                "{\"testProjectApiIds\":[\"" + API_ID + "\",\"" + secondId + "\"]}",
                context);

        JSONArray apis = JSON.parseObject(json).getJSONArray("apis");
        assertEquals(2, apis.size());
        assertEquals(String.valueOf(API_ID), apis.getJSONObject(0).getString("testProjectApiId"));
        assertEquals(String.valueOf(secondId), apis.getJSONObject(1).getString("testProjectApiId"));
    }

    @Test
    @Order(42)
    @DisplayName("超过 5 个 id 截断并标记 truncated")
    void getApiDetails_overMax_truncated() {
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(
                com.qualitest.project.domain.TestProject.builder().testProjectId(PROJECT_ID).build());
        StringBuilder ids = new StringBuilder("[");
        for (int i = 1; i <= 7; i++) {
            long id = 3000L + i;
            if (i > 1) {
                ids.append(',');
            }
            ids.append('"').append(id).append('"');
            TestProjectApi api = loginApi();
            api.setTestProjectApiId(id);
            when(mapper.selectTestProjectApiById(id)).thenReturn(api);
        }
        ids.append(']');

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAILS,
                "{\"testProjectApiIds\":" + ids + "}",
                context);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("truncated"));
        assertEquals(5, root.getJSONArray("apis").size());
    }

    @Test
    @Order(45)
    @DisplayName("字节超限时从尾部裁条并保留至少 1 条")
    void getApiDetails_byteLimit_keepsAtLeastOne() {
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(
                com.qualitest.project.domain.TestProject.builder().testProjectId(PROJECT_ID).build());
        JSONArray apis = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject row = new JSONObject();
            row.put("testProjectApiId", String.valueOf(4000 + i));
            row.put("method", "POST");
            row.put("path", "/p" + i);
            row.put("name", "n" + i);
            // 故意做大，迫使裁条
            row.put("bodyExample", "x".repeat(4000));
            apis.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("apis", apis);
        String json = GetApiDetailsTool.fitToByteLimit(result, new ArrayList<>(), 5000);
        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getJSONArray("apis").size() >= 1);
        assertTrue(root.getBooleanValue("truncated"));
        assertNotNull(root.getJSONArray("deferredIds"));
        assertFalse(root.getJSONArray("deferredIds").isEmpty());
    }

    @Test
    @Order(44)
    @DisplayName("空 testProjectApiIds 返回错误")
    void getApiDetails_emptyIds_returnsError() {
        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAILS,
                "{\"testProjectApiIds\":[]}",
                context);
        assertTrue(JSON.parseObject(json).getString("error").contains("testProjectApiIds"));
    }

    /**
     * 前提：调用未注册工具名。
     * 期望：JSON error 含「未知工具」。
     */
    @Test
    @Order(5)
    @DisplayName("未知工具名返回错误")
    void executeTool_unknownTool_returnsError() {
        String json = executor.executeTool("unknown_tool", "{}", context);
        assertTrue(JSON.parseObject(json).getString("error").contains("未知工具"));
    }

    /**
     * 前提：requestConfig 有/无 method。
     * 期望：有则转大写 POST；缺省 GET。
     */
    @Test
    @Order(6)
    @DisplayName("从 requestConfig 解析 HTTP 方法")
    void resolveMethod_fromRequestConfig() {
        assertEquals("POST", FlowDesignApiSummarizer.resolveMethod(loginApi()));
        TestProjectApi getApi = TestProjectApi.builder()
                .requestConfig("{\"method\":\"get\"}")
                .build();
        assertEquals("GET", FlowDesignApiSummarizer.resolveMethod(getApi));
    }

    /**
     * 前提：上下文图含节点 n1 与边。
     * 期望：nodeCount=1；nodes[0].id=n1；contextNodeIds 含 n1。
     */
    @Test
    @Order(7)
    @DisplayName("图摘要返回节点边与上下文")
    void getGraphSummary_returnsNodesAndEdges() {
        GraphJson graph = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(
                        GraphNode.builder()
                                .id("n1")
                                .type("http")
                                .position(GraphNodePosition.builder().x(0).y(0).build())
                                .data(new java.util.HashMap<>(java.util.Map.of("name", "登录")))
                                .build()
                )))
                .edges(new ArrayList<>(List.of(
                        GraphEdge.builder().id("e1").source("n1").target("n2").build()
                )))
                .build();
        FlowDesignToolContext graphCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(graph)
                .contextNodeIds(List.of("n1"))
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_GRAPH_SUMMARY,
                "{}",
                graphCtx);

        JSONObject root = JSON.parseObject(json);
        assertEquals(1, root.getIntValue("nodeCount"));
        assertEquals(1, root.getIntValue("edgeCount"));
        assertEquals("n1", root.getJSONArray("nodes").getJSONObject(0).getString("id"));
        assertEquals("登录", root.getJSONArray("nodes").getJSONObject(0).getString("name"));
        assertEquals("n1", root.getJSONArray("contextNodeIds").getString(0));
    }

    /**
     * 前提：按 nodeId=n1 查询。
     * 期望：返回 id/type/data.name=登录。
     */
    @Test
    @Order(8)
    @DisplayName("按 nodeId 返回节点详情")
    void getNodeDetail_validNode_returnsData() {
        GraphJson graph = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(
                        GraphNode.builder()
                                .id("n1")
                                .type("http")
                                .data(new java.util.HashMap<>(java.util.Map.of("name", "登录")))
                                .build()
                )))
                .edges(new ArrayList<>())
                .build();
        FlowDesignToolContext graphCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(graph)
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_NODE_DETAIL,
                "{\"nodeId\":\"n1\"}",
                graphCtx);

        JSONObject root = JSON.parseObject(json);
        assertEquals("n1", root.getString("id"));
        assertEquals("http", root.getString("type"));
        assertEquals("登录", root.getJSONObject("data").getString("name"));
    }

    /**
     * 前提：运行含一条 failed 步骤。
     * 期望：failed=true；failureCount=1；顶层 nodeId 与 failures[0] 一致。
     */
    @Test
    @Order(9)
    @DisplayName("失败运行返回失败步骤详情")
    void getRunFailure_failedStep_returnsDetails() {
        Long runId = 5001L;
        when(runService.selectTestFlowRunResult(runId)).thenReturn(
                TestFlowRunResult.builder()
                        .testFlowRunId(runId)
                        .testFlowId(3001L)
                        .status("failed")
                        .build());
        when(runStepService.selectTestFlowRunStepResultList(any(TestFlowRunStepParams.class)))
                .thenReturn(List.of(
                        TestFlowRunStepResult.builder()
                                .status("failed")
                                .nodeId("n1")
                                .nodeType("assert")
                                .nodeName("断言")
                                .stepIndex(2L)
                                .stepDetails("expected 200")
                                .build()
                ));
        FlowDesignToolContext runCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .contextRunId(runId)
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_RUN_FAILURE,
                "{}",
                runCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("failed"));
        assertEquals("n1", root.getString("nodeId"));
        assertEquals("断言", root.getString("nodeName"));
        assertEquals(1, root.getIntValue("failureCount"));
        assertEquals(1, root.getJSONArray("failures").size());
        assertEquals("assert", root.getJSONArray("failures").getJSONObject(0).getString("nodeType"));
        assertEquals("assert", root.getString("failureCategory"));
        assertEquals(1, root.getIntValue("assertFailureCount"));
        assertEquals(0, root.getIntValue("bizCodeFailureCount"));
        assertEquals(1, root.getJSONArray("assertFailures").size());
    }

    /**
     * 前提：画布 meta 含 activeScenarioId 与场景。
     * 期望：返回场景摘要（name、flowSeedKeys 等）。
     */
    @Test
    @Order(10)
    @DisplayName("返回场景摘要与 flowSeedKeys")
    void getFlowMeta_returnsScenarios() {
        GraphJson graph = GraphJson.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .meta(GraphMeta.builder()
                        .startNodeId("start-1")
                        .activeScenarioId("sc-default")
                        .scenarios(new ArrayList<>(List.of(
                                GraphRunScenario.builder()
                                        .id("sc-default")
                                        .name("默认")
                                        .testProjectEnvId("201")
                                        .flowSeed(new java.util.HashMap<>(java.util.Map.of("loginUser", "demo")))
                                        .remark("冒烟")
                                        .build()
                        )))
                        .flowOutputs(new ArrayList<>())
                        .build())
                .build();
        FlowDesignToolContext graphCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(graph)
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(FlowDesignToolExecutor.GET_FLOW_META, "{}", graphCtx);

        JSONObject root = JSON.parseObject(json);
        assertEquals("sc-default", root.getString("activeScenarioId"));
        assertEquals("start-1", root.getString("startNodeId"));
        assertEquals("默认", root.getJSONArray("scenarios").getJSONObject(0).getString("name"));
        assertEquals("loginUser", root.getJSONArray("scenarios").getJSONObject(0)
                .getJSONArray("flowSeedKeys").getString(0));
    }

    /**
     * 前提：项目有一条环境，envVariables 含 baseUrl 键。
     * 期望：items[0].id=201；envVarKeys 含 baseUrl（不返回值）。
     */
    @Test
    @Order(11)
    @DisplayName("列出环境摘要不含变量值")
    void listProjectEnvs_returnsEnvSummaries() {
        when(envService.selectTestProjectEnvList(any(TestProjectEnv.class))).thenReturn(List.of(
                TestProjectEnv.builder()
                        .testProjectEnvId(201L)
                        .testProjectId(PROJECT_ID)
                        .envName("本地")
                        .envUrl("http://localhost:8801")
                        .shareStatus("private")
                        .sortNum(0)
                        .envVariables("[{\"key\":\"baseUrl\"}]")
                        .delStatus(0)
                        .build()
        ));

        String json = executor.executeTool(FlowDesignToolExecutor.LIST_PROJECT_ENVS, "{}", context);

        JSONObject root = JSON.parseObject(json);
        JSONObject item = root.getJSONArray("items").getJSONObject(0);
        assertEquals("201", item.getString("id"));
        assertEquals("本地", item.getString("name"));
        assertEquals("baseUrl", item.getJSONArray("envVarKeys").getString(0));
    }

    /**
     * 前提：arguments 不是合法 JSON。
     * 期望：error=「arguments 非合法 JSON」，不进入工具实现。
     */
    @Test
    @Order(12)
    @DisplayName("非法 JSON arguments 返回错误")
    void executeTool_invalidArgumentsJson_returnsError() {
        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_NODE_DETAIL,
                "{not-json",
                context);
        assertEquals("arguments 非合法 JSON", JSON.parseObject(json).getString("error"));
    }

    /**
     * 前提：节点数超过压缩阈值，contextNodeIds 含 n5。
     * 期望：compressed=true，nodes 仅含上下文节点 n5。
     */
    @Test
    @Order(13)
    @DisplayName("超阈值压缩仅保留上下文节点")
    void getGraphSummary_compressed_includesContextNodes() {
        List<GraphNode> manyNodes = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            manyNodes.add(GraphNode.builder()
                    .id("n" + i)
                    .type("http")
                    .data(new java.util.HashMap<>(java.util.Map.of("name", "节点" + i)))
                    .build());
        }
        GraphJson graph = GraphJson.builder()
                .nodes(manyNodes)
                .edges(new ArrayList<>(List.of(
                        GraphEdge.builder().id("e1").source("n0").target("n1").build()
                )))
                .build();
        FlowDesignToolContext graphCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(graph)
                .contextNodeIds(List.of("n5"))
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(FlowDesignToolExecutor.GET_GRAPH_SUMMARY, "{}", graphCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("compressed"));
        assertEquals(1, root.getJSONArray("nodes").size());
        assertEquals("n5", root.getJSONArray("nodes").getJSONObject(0).getString("id"));
    }

    /**
     * 前提：search_apis 传 limit=2，库中有更多匹配。
     * 期望：items.size=2；truncated=true。
     */
    @Test
    @Order(14)
    @DisplayName("limit 截断并标记 truncated")
    void searchApis_limitArgument_capsAtMaxSearchApis() {
        List<TestProjectApi> many = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestProjectApi api = loginApi();
            api.setTestProjectApiId(API_ID + i);
            api.setApiName("api-" + i);
            many.add(api);
        }
        when(mapper.selectTestProjectApiList(any(TestProjectApi.class))).thenReturn(many);

        FlowDesignToolContext limitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .maxSearchApis(3)
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.SEARCH_APIS,
                "{\"keyword\":\"\",\"limit\":2}",
                limitCtx);

        JSONObject root = JSON.parseObject(json);
        assertEquals(2, root.getJSONArray("items").size());
        assertTrue(root.getBooleanValue("truncated"));
    }

    /**
     * 前提：项目下有测试流「登录流」。
     * 期望：items 含 testFlowId/flowName，不含 graphJson。
     */
    @Test
    @Order(15)
    @DisplayName("列出测试流摘要不含 graphJson")
    void listFlows_returnsSummaries() {
        when(flowService.selectTestFlowResultList(any(TestFlowParams.class))).thenReturn(List.of(
                TestFlowResult.builder()
                        .testFlowId(3001L)
                        .testProjectId(PROJECT_ID)
                        .flowName("登录流")
                        .flowDescription("演示")
                        .build()
        ));

        String json = executor.executeTool(FlowDesignToolExecutor.LIST_FLOWS, "{}", context);

        JSONObject root = JSON.parseObject(json);
        assertEquals("3001", root.getJSONArray("items").getJSONObject(0).getString("testFlowId"));
        assertEquals("登录流", root.getJSONArray("items").getJSONObject(0).getString("flowName"));
        assertFalse(root.getJSONArray("items").getJSONObject(0).containsKey("graphJson"));
    }

    /**
     * 前提：testFlowId 属于当前项目且含 graphJson。
     * 期望：返回 flowName，并解析出 graphJson.nodes。
     */
    @Test
    @Order(16)
    @DisplayName("合法测试流返回 graphJson")
    void getFlow_validFlow_returnsGraphJson() {
        when(flowService.selectTestFlowResult(3001L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(3001L)
                        .testProjectId(PROJECT_ID)
                        .flowName("登录流")
                        .graphJson("{\"nodes\":[],\"edges\":[]}")
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_FLOW,
                "{\"testFlowId\":\"3001\"}",
                context);

        JSONObject root = JSON.parseObject(json);
        assertEquals("登录流", root.getString("flowName"));
        assertTrue(root.getJSONObject("graphJson").containsKey("nodes"));
    }

    /**
     * 前提：测试流属于其它项目。
     * 期望：error=「测试流不属于当前项目」。
     */
    @Test
    @Order(17)
    @DisplayName("跨项目测试流返回归属错误")
    void getFlow_wrongProject_returnsError() {
        when(flowService.selectTestFlowResult(3001L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(3001L)
                        .testProjectId(999L)
                        .flowName("x")
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_FLOW,
                "{\"testFlowId\":\"3001\"}",
                context);

        assertEquals("测试流不属于当前项目", JSON.parseObject(json).getString("error"));
    }

    /**
     * 前提：有平台模板与项目内子流。
     * 期望：platformTemplates 非空含 templateId；projectSubflows 含 testFlowId/flowName；有 hint。
     */
    @Test
    @Order(18)
    @DisplayName("列出平台模板与项目子流")
    void listSubflowTemplates_returnsPlatformAndProject() {
        when(flowService.selectTestFlowResultList(any(TestFlowParams.class))).thenReturn(List.of(
                TestFlowResult.builder()
                        .testFlowId(3002L)
                        .testProjectId(PROJECT_ID)
                        .flowName("OAuth 子流")
                        .flowDescription("演示 OAuth")
                        .build()
        ));

        String json = executor.executeTool(FlowDesignToolExecutor.LIST_SUBFLOW_TEMPLATES, "{}", context);

        JSONObject root = JSON.parseObject(json);
        JSONArray platform = root.getJSONArray("platformTemplates");
        JSONArray project = root.getJSONArray("projectSubflows");
        assertFalse(platform.isEmpty());
        assertTrue(platform.getJSONObject(0).containsKey("templateId"));
        assertTrue(platform.getJSONObject(0).containsKey("name"));
        assertEquals(1, project.size());
        assertEquals("3002", project.getJSONObject(0).getString("testFlowId"));
        assertEquals("OAuth 子流", project.getJSONObject(0).getString("flowName"));
        assertEquals("演示 OAuth", project.getJSONObject(0).getString("flowDescription"));
        assertTrue(root.containsKey("hint"));
    }

    /**
     * 前提：被引用测试流含 http/subflow 节点与边、meta.startNodeId/flowOutputs。
     * 期望：返回拓扑摘要（flowName、startNodeId、计数、类型、outputs、nodes/edges 关键字段）。
     */
    @Test
    @Order(19)
    @DisplayName("子流详情返回拓扑摘要")
    void getSubflowDetail_returnsTopology() {
        String graphJson = """
                {
                  "nodes": [
                    {"id":"h1","type":"http","data":{"name":"Token","callMode":"external","externalUrl":"https://oauth.example.com/token","summary":"POST ↗ oauth.example.com/token"}},
                    {"id":"s1","type":"subflow","data":{"name":"内层","subflowId":"4001"}}
                  ],
                  "edges": [{"id":"e1","source":"h1","target":"s1"}],
                  "meta": {
                    "startNodeId": "h1",
                    "flowOutputs": [{"name":"token","description":"访问令牌"}]
                  }
                }
                """;
        when(flowService.selectTestFlowResult(3002L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(3002L)
                        .testProjectId(PROJECT_ID)
                        .flowName("OAuth 子流")
                        .flowDescription("演示")
                        .graphJson(graphJson)
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_SUBFLOW_DETAIL,
                "{\"testFlowId\":\"3002\"}",
                context);

        JSONObject root = JSON.parseObject(json);
        assertEquals("3002", root.getString("testFlowId"));
        assertEquals("OAuth 子流", root.getString("flowName"));
        assertEquals("演示", root.getString("flowDescription"));
        assertEquals("h1", root.getString("startNodeId"));
        assertEquals(2, root.getIntValue("nodeCount"));
        assertEquals(1, root.getIntValue("edgeCount"));
        assertEquals(1, root.getJSONObject("nodeTypeCounts").getIntValue("http"));
        assertEquals(1, root.getJSONObject("nodeTypeCounts").getIntValue("subflow"));
        assertEquals("token", root.getJSONArray("flowOutputNames").getString(0));
        assertEquals("h1", root.getJSONArray("nodes").getJSONObject(0).getString("id"));
        assertEquals("Token", root.getJSONArray("nodes").getJSONObject(0).getString("name"));
        assertEquals("external", root.getJSONArray("nodes").getJSONObject(0).getString("callMode"));
        assertEquals("s1", root.getJSONArray("nodes").getJSONObject(1).getString("id"));
        assertEquals("subflow", root.getJSONArray("nodes").getJSONObject(1).getString("type"));
        assertEquals("4001", root.getJSONArray("nodes").getJSONObject(1).getString("subflowId"));
        assertEquals("h1", root.getJSONArray("edges").getJSONObject(0).getString("source"));
        assertEquals("s1", root.getJSONArray("edges").getJSONObject(0).getString("target"));
        assertTrue(root.containsKey("hint"));
    }

    /**
     * 前提：子流 detail 请求的测试流属于其它项目。
     * 期望：error=「测试流不属于当前项目」，不泄露图结构。
     */
    @Test
    @Order(20)
    @DisplayName("跨项目子流详情返回归属错误")
    void getSubflowDetail_wrongProject_returnsError() {
        when(flowService.selectTestFlowResult(3002L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(3002L)
                        .testProjectId(999L)
                        .flowName("OAuth 子流")
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_SUBFLOW_DETAIL,
                "{\"testFlowId\":\"3002\"}",
                context);

        assertEquals("测试流不属于当前项目", JSON.parseObject(json).getString("error"));
    }

    /**
     * 前提：submit 校验通过。
     * 期望：写入 capture；received/validation.ok=true。
     */
    @Test
    @Order(21)
    @DisplayName("submit 校验通过写入 capture")
    void submitAddHttpNode_recordsCaptureAndReturnsValidation() {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSummary("登录链路");
        patch.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder().id("n1").type("http").data(new java.util.HashMap<>()).build())));
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of("HTTP 节点 API 已置空"))
                .build();
        when(normalizer.normalizeUnit(any(FlowDesignPatch.class), any(), eq(PROJECT_ID), any())).thenReturn(
                new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignToolContext submitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build())
                .maxToolResultBytes(8192)
                .submitCapture(capture)
                .build();

        String json = executor.executeTool(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(),
                "{\"op\":\"add\",\"id\":\"n1\",\"summary\":\"登录链路\",\"data\":{\"callMode\":\"project\"}}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("received"));
        assertTrue(root.getJSONObject("validation").getBooleanValue("ok"));
        assertEquals(1, root.getJSONObject("validation").getJSONArray("warnings").size());
        assertEquals(1, root.getJSONObject("patchStats").getIntValue("addNodes"));
        assertTrue(capture.isSubmitted());
        assertEquals("登录链路", capture.getNormalizedPatch().getSummary());
    }

    /**
     * 前提：submit 校验失败。
     * 期望：validation.ok=false；errors 1 条；hint 含「修正」。
     */
    @Test
    @Order(22)
    @DisplayName("submit 校验失败返回 errors")
    void submitAddEdge_validationFailed_returnsErrors() {
        FlowDesignPatch patch = new FlowDesignPatch();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(false)
                .errors(List.of("边 target 不存在"))
                .warnings(List.of())
                .build();
        when(normalizer.normalizeUnit(any(FlowDesignPatch.class), any(), eq(PROJECT_ID), any())).thenReturn(
                new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));

        FlowDesignToolContext submitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build())
                .maxToolResultBytes(8192)
                .submitCapture(new FlowDesignSubmitCapture())
                .build();

        String json = executor.executeTool(
                FlowDesignToolNames.SUBMIT_EDGE.getId(),
                "{\"op\":\"add\",\"source\":\"a\",\"target\":\"b\"}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertFalse(root.getJSONObject("validation").getBooleanValue("ok"));
        assertFalse(root.getBooleanValue("received"));
        assertEquals("校验未通过", root.getString("error"));
        assertEquals(1, root.getJSONObject("validation").getJSONArray("errors").size());
        assertTrue(root.getString("hint").contains("修正"));
    }

    /**
     * 前提：同一轮连续两次成功 submit（同或不同单元）。
     * 期望：累积接受，返回 replacedOrAppended=true。
     */
    @Test
    @Order(23)
    @DisplayName("二次 submit 返回累积标记")
    void submitAddHttpNode_secondAccepted_returnsAppendFlag() {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder().id("n2").type("http").data(new java.util.HashMap<>()).build())));
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of())
                .build();
        when(normalizer.normalizeUnit(any(FlowDesignPatch.class), any(), eq(PROJECT_ID), any())).thenReturn(
                new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch first = new FlowDesignPatch();
        first.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder().id("n1").type("http").data(new java.util.HashMap<>()).build())));
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(first, validation));
        FlowDesignToolContext submitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build())
                .submitCapture(capture)
                .build();

        String json = executor.executeTool(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(),
                "{\"op\":\"add\",\"id\":\"n2\",\"data\":{\"callMode\":\"project\"}}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("replacedOrAppended"));
        assertTrue(root.getBooleanValue("received"));
        assertEquals(2, capture.getNormalizedPatch().getAddNodes().size());
    }

    /**
     * 前提：MCP 仅传 testFlowId，上下文无 graph。
     * 期望：自动从库加载 graphJson 后返回摘要。
     */
    @Test
    @Order(24)
    @DisplayName("无图时按 testFlowId 自动加载摘要")
    void getGraphSummary_autoLoadByTestFlowId() {
        String graphJson = """
                {"nodes":[{"id":"n1","type":"http","data":{"name":"登录"}}],"edges":[]}
                """;
        when(flowService.selectTestFlowResult(3001L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(3001L)
                        .testProjectId(PROJECT_ID)
                        .flowName("登录流")
                        .graphJson(graphJson)
                        .build());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_GRAPH_SUMMARY,
                "{\"testFlowId\":\"3001\"}",
                context);

        JSONObject root = JSON.parseObject(json);
        assertEquals(1, root.getIntValue("nodeCount"));
        assertEquals("n1", root.getJSONArray("nodes").getJSONObject(0).getString("id"));
    }

    /**
     * 前提：节点绑定的 API 已不存在。
     * 期望：返回 API_MISSING，并带残留 apiName/apiPath/httpMethod 提示。
     */
    @Test
    @Order(25)
    @DisplayName("缺失 API 健康检查返回告警提示")
    void getFlowApiHealth_missingApi_returnsWarningWithHints() {
        Long missingApiId = 9999L;
        when(mapper.selectTestProjectApiById(missingApiId)).thenReturn(null);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", "客户端用户登录");
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(missingApiId));
        data.put("apiName", "客户端用户登录");
        data.put("apiPath", "/api/account/auth/login");
        data.put("httpMethod", "POST");

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(GraphNode.builder()
                        .id("n1")
                        .type("http")
                        .position(GraphNodePosition.builder().x(0).y(0).build())
                        .data(data)
                        .build()))
                .edges(new ArrayList<>())
                .build();
        FlowDesignToolContext graphCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(graph)
                .maxToolResultBytes(8192)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_FLOW_API_HEALTH,
                "{}",
                graphCtx);

        JSONObject root = JSON.parseObject(json);
        assertFalse(root.getBooleanValue("healthy"));
        assertEquals(1, root.getIntValue("warningCount"));
        assertTrue(root.getJSONArray("warningCodes").contains("API_MISSING"));
        JSONObject warning = root.getJSONArray("warnings").getJSONObject(0);
        assertEquals("API_MISSING", warning.getString("code"));
        assertEquals("n1", warning.getString("nodeId"));
        assertEquals("客户端用户登录", warning.getString("nodeName"));
        assertEquals(String.valueOf(missingApiId), warning.getString("testProjectApiId"));
        assertEquals("客户端用户登录", warning.getString("apiName"));
        assertEquals("/api/account/auth/login", warning.getString("apiPath"));
        assertEquals("POST", warning.getString("httpMethod"));
    }

    private static boolean paramSummariesContainName(JSONArray params, String name) {
        if (params == null) return false;
        for (int i = 0; i < params.size(); i++) {
            Object item = params.get(i);
            if (item instanceof JSONObject obj && name.equals(obj.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    private static TestProjectApi loginApi() {
        return TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/account/auth/login")
                .apiDescription("用户登录接口")
                .apiGroup("认证")
                .apiGroupId(3L)
                .delStatus(0)
                .authConfig("{\"mode\":\"none\"}")
                .requestConfig("""
                        %s
                        """.formatted(ApiConfigTestFixtures.LOGIN_REQUEST_CONFIG.trim()))
                .headers("{\"Content-Type\":\"application/json\"}")
                .responseConfig(ApiConfigTestFixtures.MIN_RESPONSE_CONFIG)
                .build();
    }
}
