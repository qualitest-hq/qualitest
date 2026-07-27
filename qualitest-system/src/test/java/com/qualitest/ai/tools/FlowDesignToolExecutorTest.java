package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigV2TestFixtures;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
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
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignToolExecutor：设计工具只读查询、健康检查与 submit_flow_design_patch。
 * 边界：Mock Mapper / Normalizer / 各 Service，不访问 DB 与真实 LLM；存量 begin/end 保留。
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
        executor = new FlowDesignToolExecutor(
                mapper, projectMapper, envService, flowService, runService, runStepService, normalizer,
                new HttpNodeApiHealthChecker());
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
    void searchApis_byKeyword_returnsMatchedItems() {
        begin("searchApis_byKeyword_returnsMatchedItems");
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
        assertEquals("/api/auth/login", items.getJSONObject(0).getString("path"));
        assertFalse(root.getBooleanValue("truncated"));
        log("items=" + items.size()
                + " id=" + quote(items.getJSONObject(0).getString("id"))
                + " method=" + quote(items.getJSONObject(0).getString("method"))
                + " path=" + quote(items.getJSONObject(0).getString("path")));
        end("searchApis_byKeyword_returnsMatchedItems");
    }

    /**
     * 前提：无关键词，context.scopeApiIds 已限定范围。
     * 期望：只返回 scope 内 API，不查全库。
     */
    @Test
    @Order(2)
    void searchApis_scopeApiIds_priorityWithoutKeyword() {
        begin("searchApis_scopeApiIds_priorityWithoutKeyword");
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(loginApi());

        String json = executor.executeTool(
                FlowDesignToolExecutor.SEARCH_APIS,
                "{\"keyword\":\"\"}",
                context);

        JSONArray items = JSON.parseObject(json).getJSONArray("items");
        assertEquals(1, items.size());
        assertEquals("用户登录", items.getJSONObject(0).getString("name"));
        log("items=" + items.size() + " name=" + quote(items.getJSONObject(0).getString("name")));
        end("searchApis_scopeApiIds_priorityWithoutKeyword");
    }

    /**
     * 前提：get_api_detail 传入本项目合法 testProjectApiId。
     * 期望：返回 method/path/bodyParams（含 username）等语义摘要，truncated=false。
     */
    @Test
    @Order(3)
    void getApiDetail_validApi_returnsSemanticSummary() {
        begin("getApiDetail_validApi_returnsSemanticSummary");
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(loginApi());

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAIL,
                "{\"testProjectApiId\":\"" + API_ID + "\"}",
                context);

        JSONObject root = JSON.parseObject(json);
        log("detail=" + json);
        assertEquals(String.valueOf(API_ID), root.getString("testProjectApiId"));
        assertEquals("POST", root.getString("method"));
        assertEquals("/api/auth/login", root.getString("path"));
        assertTrue(paramSummariesContainName(root.getJSONArray("bodyParams"), "username"));
        assertTrue(root.getString("bodyExample").contains("username"));
        assertNotNull(root.getJSONObject("responseSchemaSummary"));
        assertNotNull(root.getJSONObject("responseConvention"));
        assertEquals("code", root.getJSONObject("responseConvention").getString("codePath"));
        assertNotNull(root.getJSONArray("suggestedExtracts"));
        assertFalse(root.getBooleanValue("truncated"));
        end("getApiDetail_validApi_returnsSemanticSummary");
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

    /**
     * 前提：API 属于其它项目。
     * 期望：error=「接口不属于当前项目」，不泄露详情。
     */
    @Test
    @Order(4)
    void getApiDetail_wrongProject_returnsError() {
        begin("getApiDetail_wrongProject_returnsError");
        TestProjectApi otherProject = loginApi();
        otherProject.setTestProjectId(999L);
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(otherProject);

        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_API_DETAIL,
                "{\"testProjectApiId\":\"" + API_ID + "\"}",
                context);

        assertEquals("接口不属于当前项目", JSON.parseObject(json).getString("error"));
        log("error=" + quote(JSON.parseObject(json).getString("error")));
        end("getApiDetail_wrongProject_returnsError");
    }

    /**
     * 前提：调用未注册工具名。
     * 期望：JSON error 含「未知工具」。
     */
    @Test
    @Order(5)
    void executeTool_unknownTool_returnsError() {
        begin("executeTool_unknownTool_returnsError");
        String json = executor.executeTool("unknown_tool", "{}", context);
        assertTrue(JSON.parseObject(json).getString("error").contains("未知工具"));
        log("error=" + quote(JSON.parseObject(json).getString("error")));
        end("executeTool_unknownTool_returnsError");
    }

    /**
     * 前提：requestConfig 有/无 method。
     * 期望：有则转大写 POST；缺省 GET。
     */
    @Test
    @Order(6)
    void resolveMethod_fromRequestConfig() {
        begin("resolveMethod_fromRequestConfig");
        assertEquals("POST", FlowDesignApiSummarizer.resolveMethod(loginApi()));
        TestProjectApi getApi = TestProjectApi.builder()
                .requestConfig("{\"method\":\"get\"}")
                .build();
        assertEquals("GET", FlowDesignApiSummarizer.resolveMethod(getApi));
        log("loginApi=POST defaultGet=GET");
        end("resolveMethod_fromRequestConfig");
    }

    /**
     * 前提：上下文图含节点 n1 与边。
     * 期望：nodeCount=1；nodes[0].id=n1；contextNodeIds 含 n1。
     */
    @Test
    @Order(7)
    void getGraphSummary_returnsNodesAndEdges() {
        begin("getGraphSummary_returnsNodesAndEdges");
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
        log("nodeCount=1 contextNodeId=n1");
        end("getGraphSummary_returnsNodesAndEdges");
    }

    /**
     * 前提：按 nodeId=n1 查询。
     * 期望：返回 id/type/data.name=登录。
     */
    @Test
    @Order(8)
    void getNodeDetail_validNode_returnsData() {
        begin("getNodeDetail_validNode_returnsData");
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
        log("nodeId=n1 type=http");
        end("getNodeDetail_validNode_returnsData");
    }

    /**
     * 前提：运行含一条 failed 步骤。
     * 期望：failed=true；failureCount=1；顶层 nodeId 与 failures[0] 一致。
     */
    @Test
    @Order(9)
    void getRunFailure_failedStep_returnsDetails() {
        begin("getRunFailure_failedStep_returnsDetails");
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
        log("failureCount=1 nodeName=断言 failureCategory=assert");
        end("getRunFailure_failedStep_returnsDetails");
    }

    /**
     * 前提：节点绑定的 API 已不存在。
     * 期望：返回 API_MISSING，并带残留 apiName/apiPath/httpMethod 提示。
     */
    @Test
    @Order(25)
    void getFlowApiHealth_missingApi_returnsWarningWithHints() {
        begin("getFlowApiHealth_missingApi_returnsWarningWithHints");
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
        log("warningCount=1 code=API_MISSING apiPath=/api/account/auth/login");
        end("getFlowApiHealth_missingApi_returnsWarningWithHints");
    }

    /**
     * 前提：submit 校验通过。
     * 期望：写入 capture；received/validation.ok=true。
     */
    @Test
    @Order(21)
    void submitFlowDesignPatch_recordsCaptureAndReturnsValidation() {
        begin("submitFlowDesignPatch_recordsCaptureAndReturnsValidation");
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSummary("登录链路");
        patch.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder().id("n1").type("http").data(new java.util.HashMap<>()).build())));
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of("HTTP 节点 API 已置空"))
                .build();
        when(normalizer.normalize(any(FlowDesignPatch.class), any(), eq(PROJECT_ID))).thenReturn(
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
                FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH,
                "{\"summary\":\"登录链路\",\"addNodes\":[{\"id\":\"n1\",\"type\":\"http\",\"data\":{}}]}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("received"));
        assertTrue(root.getJSONObject("validation").getBooleanValue("ok"));
        assertEquals(1, root.getJSONObject("validation").getJSONArray("warnings").size());
        assertEquals(1, root.getJSONObject("patchStats").getIntValue("addNodes"));
        assertTrue(capture.isSubmitted());
        assertEquals("登录链路", capture.getNormalizedPatch().getSummary());
        log("received=true patchStats.addNodes=1");
        end("submitFlowDesignPatch_recordsCaptureAndReturnsValidation");
    }

    /**
     * 前提：submit 校验失败。
     * 期望：validation.ok=false；errors 1 条；hint 含「修正」。
     */
    @Test
    @Order(22)
    void submitFlowDesignPatch_validationFailed_returnsErrors() {
        begin("submitFlowDesignPatch_validationFailed_returnsErrors");
        FlowDesignPatch patch = new FlowDesignPatch();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(false)
                .errors(List.of("边 target 不存在"))
                .warnings(List.of())
                .build();
        when(normalizer.normalize(any(FlowDesignPatch.class), any(), eq(PROJECT_ID))).thenReturn(
                new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));

        FlowDesignToolContext submitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build())
                .maxToolResultBytes(8192)
                .submitCapture(new FlowDesignSubmitCapture())
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH,
                "{\"summary\":\"x\",\"addEdges\":[{\"source\":\"a\",\"target\":\"b\"}]}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertFalse(root.getJSONObject("validation").getBooleanValue("ok"));
        assertFalse(root.getBooleanValue("received"));
        assertEquals("校验未通过", root.getString("error"));
        assertEquals(1, root.getJSONObject("validation").getJSONArray("errors").size());
        assertTrue(root.getString("hint").contains("修正"));
        log("validationOk=false errors=1");
        end("submitFlowDesignPatch_validationFailed_returnsErrors");
    }

    /**
     * 前提：同一轮连续两次 submit。
     * 期望：后者覆盖前者，返回 replacedPrevious=true。
     */
    @Test
    @Order(23)
    void submitFlowDesignPatch_replacedPrevious_returnsFlag() {
        begin("submitFlowDesignPatch_replacedPrevious_returnsFlag");
        FlowDesignPatch patch = new FlowDesignPatch();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of())
                .build();
        when(normalizer.normalize(any(FlowDesignPatch.class), any(), eq(PROJECT_ID))).thenReturn(
                new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(patch, validation));
        FlowDesignToolContext submitCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(3001L)
                .graphJson(GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build())
                .submitCapture(capture)
                .build();

        String json = executor.executeTool(
                FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH,
                "{\"summary\":\"第二次\"}",
                submitCtx);

        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("replacedPrevious"));
        assertTrue(root.getString("hint").contains("覆盖"));
        log("replacedPrevious=true");
        end("submitFlowDesignPatch_replacedPrevious_returnsFlag");
    }

    /**
     * 前提：MCP 仅传 testFlowId，上下文无 graph。
     * 期望：自动从库加载 graphJson 后返回摘要。
     */
    @Test
    @Order(24)
    void getGraphSummary_autoLoadByTestFlowId() {
        begin("getGraphSummary_autoLoadByTestFlowId");
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
        log("autoLoad nodeCount=1");
        end("getGraphSummary_autoLoadByTestFlowId");
    }

    /**
     * 前提：画布 meta 含 activeScenarioId 与场景。
     * 期望：返回场景摘要（name、flowSeedKeys 等）。
     */
    @Test
    @Order(10)
    void getFlowMeta_returnsScenarios() {
        begin("getFlowMeta_returnsScenarios");
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
        log("activeScenarioId=sc-default startNodeId=start-1");
        end("getFlowMeta_returnsScenarios");
    }

    /**
     * 前提：项目有一条环境，envVariables 含 baseUrl 键。
     * 期望：items[0].id=201；envVarKeys 含 baseUrl（不返回值）。
     */
    @Test
    @Order(11)
    void listProjectEnvs_returnsEnvSummaries() {
        begin("listProjectEnvs_returnsEnvSummaries");
        when(envService.selectTestProjectEnvList(any(TestProjectEnv.class))).thenReturn(List.of(
                TestProjectEnv.builder()
                        .testProjectEnvId(201L)
                        .testProjectId(PROJECT_ID)
                        .envName("本地")
                        .envUrl("http://localhost:8081")
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
        log("envId=201 envVarKeys=baseUrl");
        end("listProjectEnvs_returnsEnvSummaries");
    }

    /**
     * 前提：arguments 不是合法 JSON。
     * 期望：error=「arguments 非合法 JSON」，不进入工具实现。
     */
    @Test
    @Order(12)
    void executeTool_invalidArgumentsJson_returnsError() {
        begin("executeTool_invalidArgumentsJson_returnsError");
        String json = executor.executeTool(
                FlowDesignToolExecutor.GET_NODE_DETAIL,
                "{not-json",
                context);
        assertEquals("arguments 非合法 JSON", JSON.parseObject(json).getString("error"));
        log("error=arguments 非合法 JSON");
        end("executeTool_invalidArgumentsJson_returnsError");
    }

    /**
     * 前提：节点数超过压缩阈值，contextNodeIds 含 n5。
     * 期望：compressed=true，nodes 仅含上下文节点 n5。
     */
    @Test
    @Order(13)
    void getGraphSummary_compressed_includesContextNodes() {
        begin("getGraphSummary_compressed_includesContextNodes");
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
        log("compressed=true nodes=1");
        end("getGraphSummary_compressed_includesContextNodes");
    }

    /**
     * 前提：search_apis 传 limit=2，库中有更多匹配。
     * 期望：items.size=2；truncated=true。
     */
    @Test
    @Order(14)
    void searchApis_limitArgument_capsAtMaxSearchApis() {
        begin("searchApis_limitArgument_capsAtMaxSearchApis");
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
        log("items=2 truncated=true");
        end("searchApis_limitArgument_capsAtMaxSearchApis");
    }

    /**
     * 前提：项目下有测试流「登录流」。
     * 期望：items 含 testFlowId/flowName，不含 graphJson。
     */
    @Test
    @Order(15)
    void listFlows_returnsSummaries() {
        begin("listFlows_returnsSummaries");
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
        log("testFlowId=3001 flowName=登录流");
        end("listFlows_returnsSummaries");
    }

    /**
     * 前提：testFlowId 属于当前项目且含 graphJson。
     * 期望：返回 flowName，并解析出 graphJson.nodes。
     */
    @Test
    @Order(16)
    void getFlow_validFlow_returnsGraphJson() {
        begin("getFlow_validFlow_returnsGraphJson");
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
        log("flowName=登录流 hasGraphJson=true");
        end("getFlow_validFlow_returnsGraphJson");
    }

    /**
     * 前提：测试流属于其它项目。
     * 期望：error=「测试流不属于当前项目」。
     */
    @Test
    @Order(17)
    void getFlow_wrongProject_returnsError() {
        begin("getFlow_wrongProject_returnsError");
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
        log("error=" + quote(JSON.parseObject(json).getString("error")));
        end("getFlow_wrongProject_returnsError");
    }

    /**
     * 前提：有平台模板与项目内子流。
     * 期望：platformTemplates 非空含 templateId；projectSubflows 含 testFlowId/flowName；有 hint。
     */
    @Test
    @Order(18)
    void listSubflowTemplates_returnsPlatformAndProject() {
        begin("listSubflowTemplates_returnsPlatformAndProject");
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
        log("platformCount=" + platform.size()
                + " projectCount=" + project.size()
                + " testFlowId=" + quote(project.getJSONObject(0).getString("testFlowId")));
        end("listSubflowTemplates_returnsPlatformAndProject");
    }

    /**
     * 前提：被引用测试流含 http/subflow 节点与边、meta.startNodeId/flowOutputs。
     * 期望：返回拓扑摘要（flowName、startNodeId、计数、类型、outputs、nodes/edges 关键字段）。
     */
    @Test
    @Order(19)
    void getSubflowDetail_returnsTopology() {
        begin("getSubflowDetail_returnsTopology");
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
        log("nodeCount=" + root.getIntValue("nodeCount")
                + " flowOutput=" + quote(root.getJSONArray("flowOutputNames").getString(0)));
        end("getSubflowDetail_returnsTopology");
    }

    /**
     * 前提：子流 detail 请求的测试流属于其它项目。
     * 期望：error=「测试流不属于当前项目」，不泄露图结构。
     */
    @Test
    @Order(20)
    void getSubflowDetail_wrongProject_returnsError() {
        begin("getSubflowDetail_wrongProject_returnsError");
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
        log("error=" + quote(JSON.parseObject(json).getString("error")));
        end("getSubflowDetail_wrongProject_returnsError");
    }

    private static TestProjectApi loginApi() {
        return TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/auth/login")
                .apiDescription("用户登录接口")
                .apiGroup("认证")
                .apiGroupId(3L)
                .delStatus(0)
                .requestConfig("""
                        %s
                        """.formatted(ApiConfigV2TestFixtures.LOGIN_REQUEST_CONFIG.trim()))
                .headers("{\"Content-Type\":\"application/json\"}")
                .responseConfig(ApiConfigV2TestFixtures.MIN_RESPONSE_CONFIG)
                .build();
    }
}
