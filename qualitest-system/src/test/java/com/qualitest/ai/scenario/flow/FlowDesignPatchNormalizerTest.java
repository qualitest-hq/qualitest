package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
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
 * 测 FlowDesignPatchNormalizer：AI 流程补丁规范化（补 id/position、API 绑定、summary）与预合并校验。
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
     * 期望：节点与边补数字雪花 id；节点 position=(40,80)；边 source 映射到新节点 id。
     */
    @Test
    @Order(1)
    @DisplayName("缺 id 时补雪花 id 与默认坐标")
    void normalize_assignsSnowflakeIdsAndPosition() {
        GraphNode httpNode = GraphNode.builder()
                .id("bad")
                .type("http")
                .data(new HashMap<>(Map.of("callMode", "project", "name", "登录")))
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
     * 期望：新节点落在末节点右侧一格 (420,80)。
     */
    @Test
    @Order(2)
    @DisplayName("新节点坐标跟随基图网格右移")
    void normalize_addNodePosition_followsGridFromBaseGraph() {
        GraphNode existing = GraphNode.builder()
                .id("1001")
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(new HashMap<>(Map.of("name", "登录")))
                .build();
        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(existing)))
                .edges(new ArrayList<>())
                .build();
        GraphNode add = GraphNode.builder()
                .type("http")
                .data(new HashMap<>(Map.of("name", "下一步", "callMode", "external")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(add)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        GraphNodePosition position = result.patch().getAddNodes().get(0).getPosition();
        assertNotNull(position);
        assertEquals(420.0, position.getX());
        assertEquals(80.0, position.getY());
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
        data.put("name", "非法API");
        data.put("callMode", "project");
        data.put("testProjectApiId", "not-a-number");
        GraphNode node = GraphNode.builder()
                .id("9001")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        assertNull(result.patch().getAddNodes().get(0).getData().get("testProjectApiId"));
        assertTrue(result.validation().getWarnings().stream()
                .anyMatch(w -> w.contains("testProjectApiId 无效")));
    }

    /**
     * 前提：合法 API 绑定，Mapper 返回登录接口（含 method=POST）。
     * 期望：补全 apiName；不写入 apiPath（路径只跟资产）；summary=「POST 用户登录」。
     */
    @Test
    @Order(4)
    @DisplayName("合法 API 补全字段与 summary")
    void normalize_validApi_enrichesSummaryAndFields() {
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/account/auth/login")
                .requestConfig("{\"method\":\"POST\"}")
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put("name", "登录");
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9002")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        assertEquals("用户登录", normalized.get("apiName"));
        assertNull(normalized.get("apiPath"), "节点不存 apiPath，路径只跟资产");
        assertEquals("POST 用户登录", normalized.get("summary"));
    }

    /**
     * 前提：线性 http→assert 补丁，API 合法。
     * 期望：validation.ok；assert summary=「http.body.data.code eq 0」。
     */
    @Test
    @Order(5)
    @DisplayName("线性补丁校验通过并生成 assert summary")
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
                .data(new HashMap<>(Map.of(
                        "callMode", "project",
                        "name", "登录",
                        "testProjectApiId", String.valueOf(API_ID),
                        "requestConfig", Map.of("method", "POST"),
                        "apiPath", "/api/account/auth/login",
                        "extracts", List.of(Map.of(
                                "name", "token",
                                "scope", "asset",
                                "entryKey", "clientAuth",
                                "fieldPath", "token",
                                "expr", "$.data.token",
                                "from", "body"))
                )))
                .build();
        GraphNode assertNode = GraphNode.builder()
                .id(assertId)
                .type("assert")
                .position(GraphNodePosition.builder().x(420).y(80).build())
                .data(new HashMap<>(Map.of(
                        "name", "校验 code",
                        "rules", List.of(Map.of("left", "http.body.data.code", "operator", "eq", "right", "0"))
                )))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(http, assertNode)));
        patch.setAddEdges(new ArrayList<>(List.of(
                GraphEdge.builder().id("9201").source(httpId).target(assertId).build()
        )));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        assertTrue(result.validation().isOk(), () -> String.join("; ", result.validation().getErrors()));
        assertEquals(2, result.patch().getAddNodes().size());
        assertEquals("http.body.data.code eq 0", result.patch().getAddNodes().get(1).getData().get("summary"));
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
                .data(new HashMap<>(Map.of(
                        "callMode", "project",
                        "name", "登录",
                        "testProjectApiId", String.valueOf(API_ID),
                        "apiPath", "/api/account/auth/login"
                )))
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
        assertTrue(result.validation().getWarnings().stream()
                .anyMatch(w -> w.contains("不属于当前项目")));
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
                .data(new HashMap<>(Map.of("callMode", "project", "name", "登录", "testProjectApiId", String.valueOf(API_ID))))
                .build();
        GraphNode assertNode = GraphNode.builder()
                .id(assertId)
                .type("assert")
                .position(GraphNodePosition.builder().x(420).y(80).build())
                .data(new HashMap<>(Map.of("name", "校验", "rules", List.of())))
                .build();
        GraphJson base = graphWithMeta();
        base.getNodes().addAll(List.of(http, assertNode));
        base.getEdges().add(GraphEdge.builder().id("9201").source(httpId).target(assertId).build());

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        patch.getSuggestedDeletes().setNodeIds(new ArrayList<>(List.of(assertId)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        assertTrue(result.validation().isOk(), () -> String.join("; ", result.validation().getErrors()));
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
                .data(new HashMap<>(Map.of("callMode", "project", "name", "登录")))
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
                .testProjectEnvId("")
                .flowSeed(new HashMap<>(Map.of("loginUser", "bad")))
                .remark("登录失败")
                .build();
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setAddScenarios(new ArrayList<>(List.of(newScenario)));
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setScenarioPatch(scenarioPatch);

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        GraphRunScenario normalized = result.patch().getScenarioPatch().getAddScenarios().get(0);
        assertTrue(normalized.getId().matches("\\d+"));
        assertTrue(result.validation().isOk(), () -> String.join("; ", result.validation().getErrors()));
    }

    /**
     * 前提：已有场景 flowSeed 含 loginUser；update 只带 pollAttempt。
     * 期望：浅合并后两者都在。
     */
    @Test
    @Order(10)
    @DisplayName("更新场景浅合并 flowSeed")
    void mergeScenarioPatch_updateScenario_mergesFlowSeed() {
        String scenarioId = "2042000000000000101";
        GraphMeta base = graphWithMeta().getMeta();
        base.getScenarios().get(0).setFlowSeed(new HashMap<>(Map.of("loginUser", "admin")));
        GraphRunScenario update = GraphRunScenario.builder()
                .id(scenarioId)
                .flowSeed(new HashMap<>(Map.of("pollAttempt", 1)))
                .build();
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setUpdateScenarios(new ArrayList<>(List.of(update)));
        List<String> warnings = new ArrayList<>();

        GraphMeta merged = ScenarioPatchMerger.merge(base, scenarioPatch, warnings);

        GraphRunScenario scenario = merged.getScenarios().stream()
                .filter(s -> scenarioId.equals(s.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("admin", scenario.getFlowSeed().get("loginUser"));
        assertEquals(1, scenario.getFlowSeed().get("pollAttempt"));
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
        GraphMeta base = graphWithMeta().getMeta();
        base.getScenarios().add(GraphRunScenario.builder()
                .id(removeId)
                .name("备用")
                .testProjectEnvId("")
                .flowSeed(new HashMap<>())
                .build());
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setDeleteScenarioIds(new ArrayList<>(List.of(removeId)));
        List<String> warnings = new ArrayList<>();

        GraphMeta merged = ScenarioPatchMerger.merge(base, scenarioPatch, warnings);

        assertEquals(1, merged.getScenarios().size());
        assertEquals(keepId, merged.getScenarios().get(0).getId());
    }

    /**
     * 前提：activeScenarioId 指向不存在的场景。
     * 期望：warnings 含 activeScenarioId；base 上原 active 不变。
     */
    @Test
    @Order(12)
    @DisplayName("非法 activeScenarioId 告警且不变")
    void normalize_invalidActiveScenarioId_warning() {
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setActiveScenarioId("9999999999");
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setScenarioPatch(scenarioPatch);
        GraphJson base = graphWithMeta();
        String originalActive = base.getMeta().getActiveScenarioId();

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        assertTrue(result.validation().getWarnings().stream()
                .anyMatch(w -> w.contains("activeScenarioId")));
        assertEquals(originalActive, base.getMeta().getActiveScenarioId());
    }

    /**
     * 前提：外联 HTTP 带 externalUrl 与 POST。
     * 期望：summary=「POST ↗ oauth.example.com/token」；callMode/httpMethod 保留。
     */
    @Test
    @Order(13)
    @DisplayName("外联 HTTP 生成外部 summary")
    void normalize_externalHttp_buildsExternalSummary() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "OAuth");
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder()
                .id("ext1")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        String summary = String.valueOf(normalized.get("summary"));
        assertEquals("POST ↗ oauth.example.com/token", summary);
        assertEquals("external", normalized.get("callMode"));
        assertEquals("POST", normalized.get("httpMethod"));
        assertTrue(result.validation().isOk());
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
        data.put("name", "OAuth");
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

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        assertTrue(result.validation().getWarnings().stream()
                .anyMatch(w -> w.contains("外联模式缺少 externalUrl")));
    }

    /**
     * 前提：子流节点带 subflowName 与 outputs.flowKey=token。
     * 期望：summary=「登录子流 → token」；subflowId 保留。
     */
    @Test
    @Order(15)
    @DisplayName("子流节点生成子流 summary")
    void normalize_subflow_buildsSubflowSummary() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "登录子流");
        data.put("subflowName", "登录子流");
        data.put("subflowId", "3001");
        data.put("outputs", List.of(Map.of("name", "token", "flowKey", "token")));
        GraphNode node = GraphNode.builder()
                .id("sf1")
                .type("subflow")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(data)
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(node)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, graphWithMeta(), PROJECT_ID);

        Map<String, Object> normalized = result.patch().getAddNodes().get(0).getData();
        String summary = String.valueOf(normalized.get("summary"));
        assertEquals("登录子流 → token", summary);
        assertEquals("3001", normalized.get("subflowId"));
        assertTrue(result.validation().isOk());
    }

    /**
     * 前提：空底图，AI 同批两个 addNodes 都写死 position=(40,80)。
     * 期望：第一颗保留 (40,80)，第二颗错开到 (420,80)。
     */
    @Test
    @Order(16)
    @DisplayName("同批两节点同位时第二颗网格错开")
    void normalize_sameBatchDuplicatePosition_secondShifted() {
        GraphNode a = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(new HashMap<>(Map.of("name", "A", "callMode", "external", "externalUrl", "http://x")))
                .build();
        GraphNode b = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(new HashMap<>(Map.of("name", "B", "callMode", "external", "externalUrl", "http://y")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(a, b)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, emptyGraph(), PROJECT_ID);

        GraphNodePosition p0 = result.patch().getAddNodes().get(0).getPosition();
        GraphNodePosition p1 = result.patch().getAddNodes().get(1).getPosition();
        assertEquals(40.0, p0.getX());
        assertEquals(80.0, p0.getY());
        assertEquals(420.0, p1.getX());
        assertEquals(80.0, p1.getY());
    }

    /**
     * 前提：底图已有节点 (40,80)，addNode 也写死 (40,80)。
     * 期望：即使已有 position 也错开到 (420,80)。
     */
    @Test
    @Order(17)
    @DisplayName("相对底图重叠时有坐标也错开")
    void normalize_overlapWithBase_shiftsEvenWithExplicitPosition() {
        GraphNode existing = GraphNode.builder()
                .id("1001")
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(new HashMap<>(Map.of("name", "登录")))
                .build();
        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(existing)))
                .edges(new ArrayList<>())
                .build();
        GraphNode add = GraphNode.builder()
                .type("http")
                .position(GraphNodePosition.builder().x(40.0).y(80.0).build())
                .data(new HashMap<>(Map.of("name", "下一步", "callMode", "external", "externalUrl", "http://z")))
                .build();
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(add)));

        FlowDesignPatchNormalizer.NormalizeResult result = normalizer.normalize(patch, base, PROJECT_ID);

        GraphNodePosition position = result.patch().getAddNodes().get(0).getPosition();
        assertNotNull(position);
        assertEquals(420.0, position.getX());
        assertEquals(80.0, position.getY());
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
        data.put("name", "查资料");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9301")
                .type("http")
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
        assertTrue(result.validation().getWarnings().stream()
                .anyMatch(w -> w.startsWith("AUTH_HEADER_MANAGED:")));
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
        data.put("name", "分类");
        data.put("testProjectApiId", String.valueOf(API_ID));
        GraphNode node = GraphNode.builder()
                .id("9302")
                .type("http")
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
        assertTrue(result.validation().getWarnings().stream()
                .noneMatch(w -> w.startsWith("AUTH_HEADER_MANAGED:")));
    }

    private static GraphJson emptyGraph() {
        return GraphJson.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .build();
    }

    private static GraphJson graphWithMeta() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("2042000000000000101")
                .name("默认")
                .testProjectEnvId("")
                .flowSeed(new HashMap<>())
                .build();
        GraphMeta meta = GraphMeta.builder()
                .activeScenarioId(scenario.getId())
                .scenarios(new ArrayList<>(List.of(scenario)))
                .flowOutputs(new ArrayList<>())
                .build();
        return GraphJson.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .meta(meta)
                .build();
    }
}
