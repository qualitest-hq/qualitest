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
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.formatPosition;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FlowDesignPatchNormalizer} 单元测试：AI 生成的流程补丁规范化与图校验。
 * <p>
 * LLM 输出的 {@link FlowDesignPatch} 可能缺少节点 id、绑定非法 API 等。
 * Normalizer 负责补雪花 id、补 position、校验/补全 API 绑定、生成 summary，并预合并后调用 {@link GraphJsonValidator}。
 * Mock {@link TestProjectApiMapper}，不访问数据库。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowDesignPatchNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignPatchNormalizerTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long API_ID = 2001L;

    private TestProjectApiMapper mapper;
    private FlowDesignPatchNormalizer normalizer;

    @BeforeEach
    void setUp() {
        mapper = mock(TestProjectApiMapper.class);
        normalizer = new FlowDesignPatchNormalizer(mapper, new GraphJsonValidator(), new FlowDesignPatchMerger());
    }

    /**
     * 缺 id 或非数字 id：应为节点/边补雪花 id，并为节点补默认 position。
     * 节点 id 被替换时，若连线 source 指向旧 id，应同步映射到新节点 id。
     * 期望：节点 id 为数字串；position=(40,80)；边 id 为数字串；边 source 等于新节点 id。
     */
    @Test
    @Order(1)
    void normalize_assignsSnowflakeIdsAndPosition() {
        begin("normalize_assignsSnowflakeIdsAndPosition");
        GraphNode httpNode = GraphNode.builder()
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
        log("nodeId=" + normalizedNode.getId()
                + " edgeId=" + normalizedEdge.getId()
                + " edgeSource=" + normalizedEdge.getSource()
                + " edgeTarget=" + normalizedEdge.getTarget()
                + " position=" + formatPosition(position));
        end("normalize_assignsSnowflakeIdsAndPosition");
    }

    /**
     * 已有画布节点时，缺省 position 的新增节点应落在末节点右侧一格（x+380，y 不变）。
     */
    @Test
    @Order(2)
    void normalize_addNodePosition_followsGridFromBaseGraph() {
        begin("normalize_addNodePosition_followsGridFromBaseGraph");
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
        end("normalize_addNodePosition_followsGridFromBaseGraph");
    }

    /**
     * 非法 testProjectApiId：应置空绑定并写入 warning。
     * 期望：testProjectApiId=null；warnings 含「testProjectApiId 无效」。
     */
    @Test
    @Order(3)
    void normalize_invalidApiId_clearedWithWarning() {
        begin("normalize_invalidApiId_clearedWithWarning");
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
        log("testProjectApiId=" + quote(result.patch().getAddNodes().get(0).getData().get("testProjectApiId"))
                + " warnings=" + result.validation().getWarnings());
        end("normalize_invalidApiId_clearedWithWarning");
    }

    /**
     * 合法 API 绑定：应补全 apiName、apiPath 与 HTTP summary。
     * 期望：summary=「POST /api/auth/login」。
     */
    @Test
    @Order(4)
    void normalize_validApi_enrichesSummaryAndFields() {
        begin("normalize_validApi_enrichesSummaryAndFields");
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/auth/login")
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put("name", "登录");
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(API_ID));
        data.put("requestConfig", Map.of("method", "POST"));
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
        assertEquals("/api/auth/login", normalized.get("apiPath"));
        assertEquals("POST /api/auth/login", normalized.get("summary"));
        log("apiName=" + quote(normalized.get("apiName"))
                + " apiPath=" + quote(normalized.get("apiPath"))
                + " summary=" + quote(normalized.get("summary")));
        end("normalize_validApi_enrichesSummaryAndFields");
    }

    /**
     * 线性 http→assert 补丁预合并后：图校验应通过，assert 节点应有 rules summary。
     * 期望：validation.ok=true；assert summary=「http.body.data.code eq 0」。
     */
    @Test
    @Order(5)
    void normalize_linearPatch_mergedGraphHasSingleStartNode() {
        begin("normalize_linearPatch_mergedGraphHasSingleStartNode");
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/auth/login")
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
                        "apiPath", "/api/auth/login"
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
        log("validationOk=" + result.validation().isOk()
                + " nodes=" + result.patch().getAddNodes().size()
                + " assertSummary=" + quote(result.patch().getAddNodes().get(1).getData().get("summary")));
        end("normalize_linearPatch_mergedGraphHasSingleStartNode");
    }

    /**
     * updateNodes 中非法 testProjectApiId：应置空并写入 warning。
     * 期望：testProjectApiId=null；warnings 含「不属于当前项目」。
     */
    @Test
    @Order(6)
    void normalize_updateNodeInvalidApiId_clearedWithWarning() {
        begin("normalize_updateNodeInvalidApiId_clearedWithWarning");
        GraphNode existingHttp = GraphNode.builder()
                .id("9101")
                .type("http")
                .position(GraphNodePosition.builder().x(40).y(80).build())
                .data(new HashMap<>(Map.of(
                        "callMode", "project",
                        "name", "登录",
                        "testProjectApiId", String.valueOf(API_ID),
                        "apiPath", "/api/auth/login"
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
        log("warnings=" + result.validation().getWarnings());
        end("normalize_updateNodeInvalidApiId_clearedWithWarning");
    }

    /**
     * suggestedDeletes 预合并后：被删节点不应参与图校验。
     * 期望：validation.ok=true。
     */
    @Test
    @Order(7)
    void normalize_suggestedDeletes_removedFromMergedGraph() {
        begin("normalize_suggestedDeletes_removedFromMergedGraph");
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
        log("validationOk=" + result.validation().isOk());
        end("normalize_suggestedDeletes_removedFromMergedGraph");
    }

    /**
     * updateNodes 修改已有 http 节点 API 绑定：应补全 apiName/apiPath。
     * 期望：apiName=用户登录；apiPath=/api/auth/login。
     */
    @Test
    @Order(8)
    void normalize_updateNodeValidApi_enrichesFields() {
        begin("normalize_updateNodeValidApi_enrichesFields");
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("用户登录")
                .apiPath("/api/auth/login")
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
        assertEquals("/api/auth/login", normalized.get("apiPath"));
        log("apiName=" + quote(normalized.get("apiName")));
        end("normalize_updateNodeValidApi_enrichesFields");
    }

    /**
     * addScenarios 缺 id 时补雪花 id，预合并校验通过。
     * 期望：scenario.id 为数字串；validation.ok=true。
     */
    @Test
    @Order(9)
    void normalize_addScenarios_assignsIdAndValidates() {
        begin("normalize_addScenarios_assignsIdAndValidates");
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
        log("scenarioId=" + normalized.getId() + " name=" + quote(normalized.getName()));
        end("normalize_addScenarios_assignsIdAndValidates");
    }

    /**
     * updateScenarios 浅合并 flowSeed。
     * 期望：保留 loginUser=admin；新增 pollAttempt=1。
     */
    @Test
    @Order(10)
    void mergeScenarioPatch_updateScenario_mergesFlowSeed() {
        begin("mergeScenarioPatch_updateScenario_mergesFlowSeed");
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
        log("loginUser=admin pollAttempt=1");
        end("mergeScenarioPatch_updateScenario_mergesFlowSeed");
    }

    /**
     * deleteScenarioIds 删除后仍保留至少一个场景。
     * 期望：scenarios 剩 1 条；active 场景 id 不变。
     */
    @Test
    @Order(11)
    void mergeScenarioPatch_deleteScenario_keepsAtLeastOne() {
        begin("mergeScenarioPatch_deleteScenario_keepsAtLeastOne");
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
        log("remainingScenarios=1 keepId=" + keepId);
        end("mergeScenarioPatch_deleteScenario_keepsAtLeastOne");
    }

    /**
     * 非法 activeScenarioId 产生 warning，且不修改基准图上的 activeScenarioId。
     * 期望：warnings 含 activeScenarioId；base.meta.activeScenarioId 保持原值。
     */
    @Test
    @Order(12)
    void normalize_invalidActiveScenarioId_warning() {
        begin("normalize_invalidActiveScenarioId_warning");
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
        log("warnings=" + result.validation().getWarnings().size());
        end("normalize_invalidActiveScenarioId_warning");
    }

    /**
     * 外联 HTTP 节点应生成带 ↗ 的外联 summary。
     * 期望：summary 为「POST ↗ oauth.example.com/token」；callMode、httpMethod 保留。
     */
    @Test
    @Order(13)
    void normalize_externalHttp_buildsExternalSummary() {
        begin("normalize_externalHttp_buildsExternalSummary");
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
        log("summary=" + quote(summary));
        end("normalize_externalHttp_buildsExternalSummary");
    }

    /**
     * 外联 HTTP 缺少 externalUrl 时 Normalizer 应写入 warning。
     * 期望：warnings 含「外联模式缺少 externalUrl」；不阻断 normalize。
     */
    @Test
    @Order(14)
    void normalize_externalHttp_missingExternalUrl_producesWarning() {
        begin("normalize_externalHttp_missingExternalUrl_producesWarning");
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
        log("warnings=" + result.validation().getWarnings()
                + " errors=" + result.validation().getErrors());
        end("normalize_externalHttp_missingExternalUrl_producesWarning");
    }

    /**
     * 子流节点应根据 subflowName 与 outputs 生成「名称 → flowKey」形式 summary。
     * 期望：summary 为「登录子流 → token」；subflowId 保留。
     */
    @Test
    @Order(15)
    void normalize_subflow_buildsSubflowSummary() {
        begin("normalize_subflow_buildsSubflowSummary");
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
        log("summary=" + quote(summary));
        end("normalize_subflow_buildsSubflowSummary");
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
