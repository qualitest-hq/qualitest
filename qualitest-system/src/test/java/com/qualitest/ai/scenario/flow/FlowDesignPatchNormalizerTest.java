package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.graph.FlowGraphLayeredLayout;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.api.util.AuthHeaderResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignPatchNormalizer：AI 流程补丁规范化（补雪花 id、自动排版写入新增节点坐标、
 * API 绑定）与预合并校验；含单单元将 AUTH_* / HTTP 必填写入 warnings、整包写入 errors。
 * 边界：Mock TestProjectApiMapper，不访问数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignPatchNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignPatchNormalizerTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long API_ID = 2001L;

    private TestProjectApiMapper mapper;
    private TestProjectMapper projectMapper;
    private FlowDesignPatchNormalizer normalizer;

    @BeforeEach
    void setUp() {
        mapper = mock(TestProjectApiMapper.class);
        projectMapper = mock(TestProjectMapper.class);
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig(com.qualitest.api.util.AuthProfileTestFixtures.adminThenClientJson())
                .build());
        normalizer = new FlowDesignPatchNormalizer(mapper, projectMapper, null, new GraphJsonValidator(), new FlowDesignPatchMerger());
    }

    /**
     * 前提：新增节点/边缺 id，边 source 指向将被替换的旧 id。
     * 期望：节点与边补数字雪花 id；新增节点由自动排版写入 position=(40,80)；边 source 映射到新节点 id。
     */
    @Test
    @Order(1)
    @DisplayName("缺 id 时补雪花 id 与自动排版坐标")
    void normalize_assignsSnowflakeIdsAndPosition() {
        GraphNode httpNode = GraphNode.builder()
                .id("bad")
                .type("http")
                .name("登录").data(new HashMap<>(Map.of("callMode", "project")))
                .build();
        GraphEdge edge = GraphEdge.builder()
                .source("bad")
                .target("bad2")
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(httpNode)));
        patch.setAddEdges(new ArrayList<>(List.of(edge)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID);

        GraphNode normalizedNode = result.patch().getAddNodes().get(0);
        assertTrue(normalizedNode.getId().matches("\\d+"));
        GraphNodePosition position = normalizedNode.getPosition();
        assertNotNull(position);
        assertEquals(40.0, position.getX());
        assertEquals(80.0, position.getY());
        assertTrue(result.patch().getAddEdges().get(0).getId().matches("\\d+"));
        GraphEdge normalizedEdge = result.patch().getAddEdges().get(0);
        assertEquals(normalizedNode.getId(), normalizedEdge.getSource());
        assertEquals("bad2", normalizedEdge.getTarget());
    }

    /**
     * 前提：基图画布已有节点 position=(40,80)，新增节点未带 position。
     * 期望：新节点落在末节点右侧一格（ORIGIN_X + LAYER_GAP_X, ORIGIN_Y）。
     */
    @Test
    @Order(2)
    @DisplayName("新节点坐标跟随基图网格右移")
    void normalize_addNodePosition_followsGridFromBaseGraph() {
        GraphNode existing = GraphNode.builder()
                .id("1001")
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .name("登录").data(new HashMap<>())
                .build();
        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(existing)))
                .edges(new ArrayList<>())
                .build();
        GraphNode add = GraphNode.builder()
                .type("http")
                .name("下一步").data(new HashMap<>(Map.of("callMode", "external")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(add)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        GraphNodePosition position = result.patch().getAddNodes().get(0).getPosition();
        assertNotNull(position);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X, position.getX());
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, position.getY());
    }

    /**
     * 前提：project 模式节点的 testProjectApiId 非数字。
     * 期望：绑定置空；warnings 含「testProjectApiId 无效」。
     */
    @Test
    @Order(3)
    @DisplayName("非法 API id 置空并告警")
    void normalize_invalidApiId_clearedWithWarning() {
        Map<String, Object> data = new HashMap<>();
        String shellName = "非法API";
        data.put("callMode", "project");
        data.put("testProjectApiId", "not-a-number");
        GraphNode node = GraphNode.builder()
                .id("9001")
                .type("http")
                .name("非法API")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        assertNull(result.patch().getAddNodes().get(0).getData().get("testProjectApiId"));
    }

    /**
     * 前提：合法 API 绑定，Mapper 返回登录接口（含 method=POST）。
     * 期望：补全 apiName；不写入 apiPath（路径只跟资产）；不在 data 落展示摘要。
     */
    @Test
    @Order(4)
    @DisplayName("合法 API 补全字段")
    void normalize_validApi_enrichesFields() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/account/auth/login")
                .requestConfig("{\"method\":\"POST\"}")
                .build());

        Map<String, Object> data = new HashMap<>();
        String shellName = "登录";
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9002")
                .type("http")
                .name("登录")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        assertEquals("用户登录", normalized.get("apiName"));
        assertNull(normalized.get("apiPath"), "节点不存 apiPath，路径只跟资产");
    }

    /**
     * 前提：线性 http→assert 补丁，API 合法。
     * 期望：validation.ok。
     */
    @Test
    @Order(5)
    @DisplayName("线性补丁校验通过")
    void normalize_linearPatch_mergedGraphHasSingleStartNode() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/account/auth/login")
                .build());

        String httpId = "9101";
        String assertId = "9102";
        GraphNode http = GraphNode.builder()
                .id(httpId)
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("登录").data(new HashMap<>(Map.of("callMode", "project",
                        "testProjectApiId", String.valueOf(API_ID),
                        "requestConfig", Map.of("method", "POST"),
                        "apiPath", "/api/account/auth/login",
                        "extracts", List.of(Map.of(
                                "name", "token",
                                "scope", "asset",
                                "entryKey", "clientAuth",
                                "fieldPath", "token",
                                "expr", "$.data.token",
                                "from", "body")))))
                .build();
        GraphNode assertNode = GraphNode.builder()
                .id(assertId)
                .type("assert")
                .position(GraphNodePosition.builder().x(420).y(80).build())
                .name("校验 code").data(new HashMap<>(Map.of("rules", List.of(Map.of("left", "http.body.data.code", "operator", "eq", "right", "0")))))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(http, assertNode)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("9200").source("entry-test-1").target(httpId).build(),
                GraphEdge.builder().id("9201").source(httpId).target(assertId).build()
        )));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        assertTrue(result.ok(), () -> String.join("; ", result.errors()));
        assertEquals(2, result.patch().getAddNodes().size());
    }

    /**
     * 前提：updateNodes 写入不属于当前项目的 testProjectApiId。
     * 期望：置空绑定；warnings 含「不属于当前项目」。
     */
    @Test
    @Order(6)
    @DisplayName("更新节点跨项目 API 置空告警")
    void normalize_updateNodeInvalidApiId_clearedWithWarning() {
        GraphNode existingHttp = GraphNode.builder()
                .id("9101")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("登录").data(new HashMap<>(Map.of("callMode", "project",
                        "testProjectApiId", String.valueOf(API_ID),
                        "apiPath", "/api/account/auth/login")))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().add(existingHttp);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("testProjectApiId", "9999999");
        GraphNode update = GraphNode.builder()
                .id("9101")
                .data(updateData)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(new ArrayList<>(List.of(update)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        assertNull(result.patch().getUpdateNodes().get(0).getData().get("testProjectApiId"));
    }

    /**
     * 前提：suggestedDeletes 删除 assert 节点后预合并。
     * 期望：被删节点不参与校验，validation.ok。
     */
    @Test
    @Order(7)
    @DisplayName("suggestedDeletes 预合并校验通过")
    void normalize_suggestedDeletes_removedFromMergedGraph() {
        String httpId = "9101";
        String assertId = "9102";
        GraphNode http = GraphNode.builder()
                .id(httpId)
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("登录").data(new HashMap<>(Map.of("callMode", "project", "testProjectApiId", String.valueOf(API_ID))))
                .build();
        GraphNode assertNode = GraphNode.builder()
                .id(assertId)
                .type("assert")
                .position(GraphNodePosition.builder().x(420).y(80).build())
                .name("校验").data(new HashMap<>(Map.of("rules", List.of())))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().addAll(List.of(http, assertNode));
        base.getEdges().add(GraphEdge.builder().id("9199").source("entry-test-1").target(httpId).build());
        base.getEdges().add(GraphEdge.builder().id("9201").source(httpId).target(assertId).build());

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        patch.getSuggestedDeletes().setNodeIds(new ArrayList<>(List.of(assertId)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        assertTrue(result.ok(), () -> String.join("; ", result.errors()));
    }

    /**
     * 前提：updateNodes 为已有 http 节点绑定合法 API。
     * 期望：补全 apiName=用户登录；不写入 apiPath。
     */
    @Test
    @Order(8)
    @DisplayName("更新节点合法 API 补全字段")
    void normalize_updateNodeValidApi_enrichesFields() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/account/auth/login")
                .build());

        GraphNode existingHttp = GraphNode.builder()
                .id("9101")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("登录").data(new HashMap<>(Map.of("callMode", "project")))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().add(existingHttp);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode update = GraphNode.builder()
                .id("9101")
                .data(updateData)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(new ArrayList<>(List.of(update)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        Map<String, Object> normalized = result.patch().getUpdateNodes().get(0).getData();
        assertEquals("用户登录", normalized.get("apiName"));
        assertNull(normalized.get("apiPath"), "节点不存 apiPath，路径只跟资产");
    }

    /**
     * 前提：addScenarios 缺 id。
     * 期望：补数字雪花 id；validation.ok。
     */
    @Test
    @Order(9)
    @DisplayName("新增场景补 id 且校验通过")
    void normalize_addScenarios_assignsIdAndValidates() {
        GraphRunScenario newScenario = GraphRunScenario.builder()
                .name("异常路径")
                .envId("")
                .inSeed(new HashMap<>(Map.of("loginUser", "bad")))
                .remark("登录失败")
                .build();
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setAddScenarios(new ArrayList<>(List.of(newScenario)));
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setScenarioPatch(scenarioPatch);

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        GraphRunScenario normalized = result.patch().getScenarioPatch().getAddScenarios().get(0);
        assertTrue(normalized.getId().matches("\\d+"));
        assertTrue(result.ok(), () -> String.join("; ", result.errors()));
    }

    /**
     * 前提：已有场景 inSeed 含 loginUser；update 只带 pollAttempt。
     * 期望：浅合并后两者都在。
     */
    @Test
    @Order(10)
    @DisplayName("更新场景浅合并 inSeed")
    void mergeScenarioPatch_updateScenario_mergesFlowSeed() {
        String scenarioId = "2042000000000000101";
        List<GraphRunScenario> base = new ArrayList<>(com.qualitest.flow.context.EntryScenarioSupport
                .allEntryScenarios(graphWithScenarioEntry()));
        base.get(0).setInSeed(new HashMap<>(Map.of("loginUser", "admin")));
        GraphRunScenario update = GraphRunScenario.builder()
                .id(scenarioId)
                .inSeed(new HashMap<>(Map.of("pollAttempt", 1)))
                .build();
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setUpdateScenarios(new ArrayList<>(List.of(update)));
        List<String> warnings = new ArrayList<>();

        List<GraphRunScenario> merged = ScenarioPatchMerger.merge(base, scenarioPatch, warnings);

        GraphRunScenario scenario = merged.stream()
                .filter(s -> scenarioId.equals(s.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("admin", scenario.getInSeed().get("loginUser"));
        assertEquals(1, scenario.getInSeed().get("pollAttempt"));
    }

    /**
     * 前提：两个场景，删除其中一个。
     * 期望：仍剩 1 条，且为原先保留的 keepId。
     */
    @Test
    @Order(11)
    @DisplayName("删除场景至少保留一条")
    void mergeScenarioPatch_deleteScenario_keepsAtLeastOne() {
        String keepId = "2042000000000000101";
        String removeId = "2042000000000000102";
        List<GraphRunScenario> base = new ArrayList<>(com.qualitest.flow.context.EntryScenarioSupport
                .allEntryScenarios(graphWithScenarioEntry()));
        base.add(GraphRunScenario.builder()
                .id(removeId)
                .name("备用")
                .envId("")
                .inSeed(new HashMap<>())
                .build());
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setDeleteScenarioIds(new ArrayList<>(List.of(removeId)));
        List<String> warnings = new ArrayList<>();

        List<GraphRunScenario> merged = ScenarioPatchMerger.merge(base, scenarioPatch, warnings);

        assertEquals(1, merged.size());
        assertEquals(keepId, merged.get(0).getId());
    }

    /**
     * 前提：外联 HTTP 带 externalUrl 与 POST。
     * 期望：不在 data 落展示摘要；callMode/httpMethod 保留。
     */
    @Test
    @Order(12)
    @DisplayName("外联 HTTP 不落 data 摘要")
    void normalize_noLongerWritesDataSummary() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder()
                .id("ext1")
                .type("http")
                .name("OAuth")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e-ext1").source("entry-test-1").target("ext1").build())));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        assertEquals("external", normalized.get("callMode"));
        assertEquals("POST", normalized.get("httpMethod"));
        assertTrue(result.ok());
    }

    /**
     * 前提：外联 HTTP 缺少 externalUrl。
     * 期望：warnings 含「外联模式缺少 externalUrl」，不阻断 normalize。
     */
    @Test
    @Order(14)
    @DisplayName("外联缺 URL 产生 warning")
    void normalize_externalHttp_missingExternalUrl_producesWarning() {
        Map<String, Object> data = new HashMap<>();
        String shellName = "OAuth";
        data.put("callMode", "external");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder()
                .id("ext2")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e-ext2").source("entry-test-1").target("ext2").build())));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        // 规范化侧 effect：缺 URL 仍接受进严格快照；提示仅内部 warnings，不进 NormalizeResult
        assertNotNull(result.patch());
    }

    /**
     * 前提：子流节点带 subflowName 与未知键 legacyFoo。
     * 期望：不在 data 落展示摘要；subflowId 保留；未知键被剔除。
     */
    @Test
    @Order(15)
    @DisplayName("子流节点不落 data 摘要，剔除未知键")
    void normalize_subflow_noDataSummary() {
        Map<String, Object> data = new HashMap<>();
        data.put("subflowName", "登录子流");
        data.put("subflowId", "3001");
        data.put("legacyFoo", List.of(Map.of("name", "token")));
        GraphNode node = GraphNode.builder()
                .id("sf1")
                .type("subflow")
                .name("登录子流")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e-sf1").source("entry-test-1").target("sf1").build())));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        assertEquals("3001", normalized.get("subflowId"));
        assertFalse(normalized.containsKey("legacyFoo"));
        assertTrue(result.ok());
    }

    /**
     * 前提：空底图，AI 同批两个 addNodes 都写死 position=(40,80)。
     * 期望：忽略入参坐标后自动排版；第一颗 ORIGIN，第二颗右移一格 LAYER_GAP_X。
     */
    @Test
    @Order(16)
    @DisplayName("同批两节点同位时第二颗网格错开")
    void normalize_sameBatchDuplicatePosition_secondShifted() {
        GraphNode a = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .name("A").data(new HashMap<>(Map.of("callMode", "external", "externalUrl", "http://x")))
                .build();
        GraphNode b = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .name("B").data(new HashMap<>(Map.of("callMode", "external", "externalUrl", "http://y")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(a, b)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID);

        GraphNodePosition p0 = result.patch().getAddNodes().get(0).getPosition();
        GraphNodePosition p1 = result.patch().getAddNodes().get(1).getPosition();
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, p0.getX());
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, p0.getY());
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X, p1.getX());
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, p1.getY());
    }

    /**
     * 前提：底图已有节点 (40,80)，addNode 也写死 (40,80)。
     * 期望：忽略入参坐标，自动避让后落在 ORIGIN_X + LAYER_GAP_X。
     */
    @Test
    @Order(17)
    @DisplayName("与底图重叠时自动避让错开")
    void normalize_overlapWithBase_shiftsEvenWithExplicitPosition() {
        GraphNode existing = GraphNode.builder()
                .id("1001")
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .name("登录").data(new HashMap<>())
                .build();
        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(existing)))
                .edges(new ArrayList<>())
                .build();
        GraphNode add = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .name("下一步").data(new HashMap<>(Map.of("callMode", "external", "externalUrl", "http://z")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(add)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        GraphNodePosition position = result.patch().getAddNodes().get(0).getPosition();
        assertNotNull(position);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X, position.getX());
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, position.getY());
    }

    /**
     * 前提：绑定需登录接口且节点未写 Authorization；项目已有双端 Profile。
     * 期望：补 profileManaged Authorization=Bearer {{asset.clientAuth.token}}，并有鉴权补全 warning。
     */
    @Test
    @Order(16)
    @DisplayName("需登录接口缺头时补托管 Authorization")
    void normalize_addsManagedAuthHeader() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("当前用户")
                .apiPath("/api/account/auth/profile")
                .authConfig("{\"mode\":\"inherit\",\"authProfileId\":\"clientBearer\"}")
                .build());
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9301")
                .type("http")
                .name("查资料")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> headers =
                (List<Map<String, Object>>) result.patch().getAddNodes().get(0).getData().get("headers");
        assertNotNull(headers);
        assertFalse(headers.isEmpty());
        Map<String, Object> auth = headers.stream()
                .filter(h -> "Authorization".equalsIgnoreCase(String.valueOf(h.get("name"))))
                .findFirst()
                .orElse(null);
        assertNotNull(auth);
        assertEquals("Bearer {{asset.clientAuth.token}}", auth.get("value"));
        assertTrue(AuthHeaderResolver.isProfileManaged(auth));
    }

    /**
     * 前提：mode=none 的公开接口。
     * 期望：不补 Authorization。
     */
    @Test
    @Order(17)
    @DisplayName("免登录接口不补鉴权头")
    void normalize_noneMode_noAuthHeader() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("分类列表")
                .apiPath("/api/mall/category/list")
                .authConfig("{\"mode\":\"none\"}")
                .build());
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9302")
                .type("http")
                .name("分类")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Object headers = result.patch().getAddNodes().get(0).getData().get("headers");
        if (headers instanceof List<?> list) {
            boolean hasAuth = list.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> (Map<?, ?>) m)
                    .anyMatch(h -> "Authorization".equalsIgnoreCase(String.valueOf(h.get("name"))));
            assertFalse(hasAuth);
        }
    }

    @Test
    @Order(90)
    @DisplayName("短名节点 id 映射进会话 map，同会话 update 复用雪花")
    void normalize_clientAlias_stableAcrossRounds() {
        Map<String, String> sessionMap = new HashMap<>();
        GraphNode n1 = GraphNode.builder()
                .id("n_login")
                .type("http")
                .name("登录").data(new HashMap<>(Map.of("callMode", "project")))
                .build();
        FlowDesignPatch add = new FlowDesignPatch();
        add.setAddNodes(new ArrayList<>(List.of(n1)));

        FlowDesignPatchNormalizer.NormalizeResult r1 = normalizer.normalize(add, emptyGraph(), PROJECT_ID, sessionMap);
        assertTrue(sessionMap.containsKey("n_login"));
        String snowflake = sessionMap.get("n_login");
        assertEquals(snowflake, r1.patch().getAddNodes().get(0).getId());

        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(GraphNode.builder().id(snowflake).type("http").name("登录").data(new HashMap<>()).build())))
                .edges(new ArrayList<>())
                .build();
        GraphNode upd = GraphNode.builder()
                .id("n_login")
                .type("http")
                .name("登录改").data(new HashMap<>(Map.of("callMode", "project")))
                .build();
        FlowDesignPatch update = new FlowDesignPatch();
        update.setUpdateNodes(new ArrayList<>(List.of(upd)));

        FlowDesignPatchNormalizer.NormalizeResult r2 = normalizer.normalize(update, base, PROJECT_ID, sessionMap);
        assertEquals(snowflake, r2.patch().getUpdateNodes().get(0).getId());
        assertFalse(r2.errors().stream().anyMatch(e -> e.contains("未知节点")));
    }

    @Test
    @Order(91)
    @DisplayName("出口只认边：错误 target 被边覆盖；无出边分支清 target")
    void normalize_edgeWinsOverWrongTarget() {
        Map<String, String> sessionMap = new HashMap<>();
        Map<String, Object> condData = new HashMap<>();
        condData.put("branches", new ArrayList<>(List.of(
                new HashMap<>(Map.of(
                        "id", "b_if",
                        "kind", "if",
                        "target", "n_wrong",
                        "conditions", List.of(Map.of("left", "asset.adminAuth.token", "operator", "exists")))),
                new HashMap<>(Map.of(
                        "id", "b_else",
                        "kind", "else",
                        "target", "n_wrong"))
        )));
        GraphNode cond = GraphNode.builder().id("n_cond").type("condition").name("分支").data(condData).build();
        GraphNode probe = GraphNode.builder().id("n_probe").type("http")
                .name("探活").data(new HashMap<>(Map.of("callMode", "project"))).build();
        GraphNode login = GraphNode.builder().id("n_login").type("http")
                .name("登录").data(new HashMap<>(Map.of("callMode", "project"))).build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(cond, probe, login)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e1").source("n_cond").target("n_probe").label("if").build(),
                GraphEdge.builder().id("e2").source("n_cond").target("n_login").label("else").build()
        )));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID, sessionMap);
        GraphNode condNode = nodeByDataName(result.patch().getAddNodes(), "分支");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) condNode.getData().get("branches");
        Map<String, Object> ifBranch = branchById(branches, "b_if");
        Map<String, Object> elseBranch = branchById(branches, "b_else");
        assertEquals(sessionMap.get("n_probe"), ifBranch.get("target"));
        assertEquals(sessionMap.get("n_login"), elseBranch.get("target"));
    }

    @Test
    @Order(92)
    @DisplayName("无出边的分支清除 target；仅 kind 生效不读 type")
    void normalize_noEdgeClearsTarget_ignoresTypeLabel() {
        Map<String, String> sessionMap = new HashMap<>();
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("branches", new ArrayList<>(List.of(
                new HashMap<>(Map.of(
                        "id", "b_ok",
                        "kind", "if",
                        "target", "n_login",
                        "conditions", List.of(Map.of("left", "http.status", "operator", "eq", "right", "200")))),
                new HashMap<>(Map.of(
                        "id", "b_fail",
                        "kind", "else",
                        "target", "n_login"))
        )));
        // type=ELSE 但无 kind 的脏分支不应被当成 else 去匹配边
        Map<String, Object> dirty = new HashMap<>();
        dirty.put("id", "b_dirty");
        dirty.put("type", "ELSE");
        dirty.put("target", "n_login");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) statusData.get("branches");
        raw.add(dirty);

        GraphNode status = GraphNode.builder().id("n_status").type("condition").name("探活是否200").data(statusData).build();
        GraphNode login = GraphNode.builder().id("n_login").type("http")
                .name("登录").data(new HashMap<>(Map.of("callMode", "project"))).build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(status, login)));
        // 仅 ELSE 有出边；IF 无出边应清 target
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e4").source("n_status").target("n_login").label("else").build()
        )));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID, sessionMap);
        GraphNode statusNode = nodeByDataName(result.patch().getAddNodes(), "探活是否200");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) statusNode.getData().get("branches");
        Map<String, Object> ok = branchById(branches, "b_ok");
        Map<String, Object> fail = branchById(branches, "b_fail");
        Map<String, Object> dirtyBranch = branchById(branches, "b_dirty");
        assertNull(ok.get("target"));
        assertEquals(sessionMap.get("n_login"), fail.get("target"));
        // 无 kind：不按 type=ELSE 匹配 label，且 else 边已被占用 → target 清空
        assertNull(dirtyBranch.get("target"));
    }

    @Test
    @Order(93)
    @DisplayName("updateNodes 未知短名报错且不发新号")
    void normalize_unknownUpdateAlias_errors() {
        Map<String, String> sessionMap = new HashMap<>();
        GraphNode upd = GraphNode.builder()
                .id("never_seen")
                .type("http")
                .name("x").data(new HashMap<>(Map.of("callMode", "project")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(new ArrayList<>(List.of(upd)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID, sessionMap);
        assertFalse(result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("never_seen")));
        assertFalse(sessionMap.containsKey("never_seen"));
    }

    /**
     * 前提：项目双端鉴权；单单元 add 需登录的客户端 project HTTP，图中无 token 来源。
     * 期望：normalizeUnit 地板通过且 errors 无 AUTH；整包严格快照 errors 含 AUTH_TOKEN_MISSING。
     */
    @Test
    @Order(94)
    @DisplayName("normalizeUnit 不因 AuthToken 门禁拒收")
    void normalizeUnit_projectHttpMissingToken_skipsAuthGate() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("资料")
                .apiPath("/api/account/auth/profile")
                .authConfig("{\"mode\":\"inherit\"}")
                .requestConfig("{\"method\":\"GET\"}")
                .build());

        FlowDesignPatch unitPatch = dualBearerHttpAddPatch("9601");
        FlowDesignPatchNormalizer.NormalizeResult unitResult =
                normalizer.normalizeUnit(unitPatch, graphWithMeta(), PROJECT_ID, new HashMap<>());
        assertTrue(unitResult.ok(), () -> "unit errors=" + unitResult.errors());
        assertTrue(unitResult.errors().stream().noneMatch(e -> e.startsWith("AUTH_TOKEN_MISSING:")),
                () -> "unit errors=" + unitResult.errors());

        FlowDesignPatch fullPatch = dualBearerHttpAddPatch("9602");
        FlowDesignPatchNormalizer.NormalizeResult fullResult =
                normalizer.normalize(fullPatch, graphWithMeta(), PROJECT_ID);
        assertTrue(fullResult.errors().stream().anyMatch(e -> e.startsWith("AUTH_TOKEN_MISSING:")),
                () -> "full errors=" + fullResult.errors());
    }

    /** 构造绑定客户端 inherit HTTP 的 addNode 补丁（缺 token 来源）。 */
    private static FlowDesignPatch dualBearerHttpAddPatch(String nodeId) {
        Map<String, Object> data = new HashMap<>();
        String shellName = "资料";
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id(nodeId)
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));
        return patch;
    }

    /**
     * 前提：updateNodes 把已有非空 rules 改成空数组。
     * 期望：normalizeUnit 以 idErrors 阻断（疑似误清空）。
     */
    @Test
    @Order(95)
    @DisplayName("update 空 rules 相对 baseline 非空则阻断")
    void normalizeUnit_updateEmptyRules_blocksWhenBaselineNonEmpty() {
        GraphNode existing = GraphNode.builder()
                .id("9101")
                .type("assert")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("校验").data(new HashMap<>(Map.of("rules", List.of(Map.of("left", "flow.code", "operator", "eq", "right", "0")))))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().add(existing);

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(new ArrayList<>(List.of(GraphNode.builder()
                .id("9101")
                .data(new HashMap<>(Map.of("rules", List.of())))
                .build())));

        FlowDesignPatchNormalizer.NormalizeResult result =
                normalizer.normalizeUnit(patch, base, PROJECT_ID, new HashMap<>());
        assertFalse(result.ok());
        assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("data.rules") && e.contains("空数组")));
    }

    /**
     * 前提：condition update 带 branches[].target。
     * 期望：规范化后 target 被剔除（无匹配出边时保持无 target）。
     */
    @Test
    @Order(96)
    @DisplayName("condition 预写 target 被剔除")
    void normalize_conditionTargetStripped() {
        GraphNode existing = GraphNode.builder()
                .id("9101")
                .type("condition")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .name("分支").data(new HashMap<>(Map.of("branches", List.of(
                                new HashMap<>(Map.of(
                                        "id", "b1",
                                        "kind", "if",
                                        "target", "ghost-node",
                                        "conditions", List.of(Map.of(
                                                "left", "flow.code",
                                                "operator", "eq",
                                                "right", "0"))
                                )),
                                new HashMap<>(Map.of(
                                        "id", "b2",
                                        "kind", "else",
                                        "conditions", List.of()
                                ))
                        ))))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().add(existing);

        FlowDesignPatch patch = new FlowDesignPatch();
        Map<String, Object> ifBranch = new HashMap<>();
        ifBranch.put("id", "b1");
        ifBranch.put("kind", "if");
        ifBranch.put("target", "should-be-stripped");
        ifBranch.put("conditions", List.of(Map.of("left", "flow.code", "operator", "eq", "right", "0")));
        Map<String, Object> elseBranch = new HashMap<>();
        elseBranch.put("id", "b2");
        elseBranch.put("kind", "else");
        elseBranch.put("conditions", List.of());
        patch.setUpdateNodes(new ArrayList<>(List.of(GraphNode.builder()
                .id("9101")
                .type("condition")
                .data(new HashMap<>(Map.of(
                        "branches", new ArrayList<>(List.of(ifBranch, elseBranch))
                )))
                .build())));

        FlowDesignPatchNormalizer.NormalizeResult result =
                normalizer.normalizeUnit(patch, base, PROJECT_ID, new HashMap<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches =
                (List<Map<String, Object>>) result.patch().getUpdateNodes().get(0).getData().get("branches");
        assertFalse(branches.get(0).containsKey("target"));
    }

    /**
     * 前提：ELSE 分支 id 为 else_cred，出边 label 同为 else_cred；IF 无出边。
     * 期望：ELSE 写入登录节点 target，IF 无 target。
     */
    @Test
    @Order(97)
    @DisplayName("label=else_cred 按 branchId 回写 ELSE target")
    void normalize_edgeLabelBranchId_bindsElse() {
        Map<String, String> sessionMap = new HashMap<>();
        Map<String, Object> condData = new HashMap<>();
        condData.put("branches", new ArrayList<>(List.of(
                new HashMap<>(Map.of(
                        "id", "b_if",
                        "kind", "if",
                        "conditions", List.of(Map.of("left", "asset.adminAuth.token", "operator", "exists")))),
                new HashMap<>(Map.of(
                        "id", "else_cred",
                        "kind", "else"))
        )));
        GraphNode cond = GraphNode.builder().id("n_cond").type("condition").name("凭证是否存在").data(condData).build();
        GraphNode login = GraphNode.builder().id("n_login").type("http")
                .name("登录").data(new HashMap<>(Map.of("callMode", "project"))).build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(cond, login)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("e_else").source("n_cond").target("n_login").label("else_cred").build()
        )));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID, sessionMap);
        GraphNode condNode = nodeByDataName(result.patch().getAddNodes(), "凭证是否存在");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) condNode.getData().get("branches");
        Map<String, Object> ifBranch = branchById(branches, "b_if");
        Map<String, Object> elseBranch = branchById(branches, "else_cred");
        assertNull(ifBranch.get("target"));
        assertEquals(sessionMap.get("n_login"), elseBranch.get("target"));
    }

    private static Map<String, Object> branchById(List<Map<String, Object>> branches, String id) {
        return branches.stream()
                .filter(b -> id.equals(String.valueOf(b.get("id"))))
                .findFirst()
                .orElseThrow();
    }

    private static GraphNode nodeByDataName(List<GraphNode> nodes, String name) {
        return nodes.stream()
                .filter(n -> name.equals(n.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static GraphJson emptyGraph() {
        return GraphJson.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .build();
    }

    private static GraphJson graphWithMeta() {
        return graphWithScenarioEntry();
    }

    private static GraphJson graphWithScenarioEntry() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("2042000000000000101")
                .name("默认")
                .envId("")
                .inSeed(new HashMap<>())
                .build();
        Map<String, Object> data = new HashMap<>();
        data.put("scenarios", List.of(scenario));
        GraphNode entry = GraphNode.builder()
                .id("entry-test-1")
                .type("scenario-entry")
                .name("默认场景入口")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .data(data)
                .build();
        return GraphJson.builder()
                .nodes(new ArrayList<>(List.of(entry)))
                .edges(new ArrayList<>())
                .meta(GraphMeta.builder().build())
                .build();
    }
}
