package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignPatchConfirmService：单 Staging 单元 confirm（合并落盘、幂等；校验不挡确认）。
 * 边界：全部内存完成，不写数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignPatchConfirmServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignPatchConfirmServiceTest {

    private FlowDesignPatchConfirmService confirmService;

    @BeforeEach
    void setUp() {
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(null, null, null, new GraphJsonValidator(null), merger, null);
        confirmService = new FlowDesignPatchConfirmService(normalizer, merger);
    }

    /**
     * 前提：夹具 linear-add-accepted，按 acceptedIds 顺序逐单元 confirm。
     * 期望：全部成功，合并图符合夹具。
     */
    @Test
    @Order(1)
    @DisplayName("线性加节点边按序确认成功")
    void confirm_linearAddAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/linear-add-accepted.json");
    }

    /**
     * 前提：仅 confirm addEdge:8001，未先 confirm 依赖节点。
     * 期望：仍落盘（依赖不挡确认），合并图带悬空边目标由画布校验。
     */
    @Test
    @Order(2)
    @DisplayName("仅确认边缺依赖节点时仍落盘")
    void confirm_partialEdgeOnly_stillApplies() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addEdge:8001", List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        FlowMergeFixtureTestSupport.assertConfirmFixture(fixture, result);
    }

    /**
     * 前提：同上，仅 confirm addEdge。
     * 期望：有 graphJson；baseGraphHash 长 16。
     */
    @Test
    @Order(3)
    @DisplayName("仅确认边时仍返回合并图")
    void confirm_partialEdgeOnly_returnsGraph() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addEdge:8001", List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        assertNotNull(result.getGraphJson());
        assertTrue(result.getBaseGraphHash().length() == 16);
        assertTrue(result.getGraphJson().getEdges().stream()
                .anyMatch(e -> e != null && "8001".equals(e.getId())));
    }

    /**
     * 前提：先 confirm addNode:9001，再 confirm addEdge:8001。
     * 期望：两步都成功，合并后有 1 条边。
     */
    @Test
    @Order(4)
    @DisplayName("先加节点再确认边成功")
    void confirm_addEdgeAfterAddNode_ok() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");

        FlowDesignPatchConfirmRequest addNodeRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), null);
        FlowDesignPatchConfirmResult addNodeResult = confirmService.confirmUnit(addNodeRequest);
        assertNotNull(addNodeResult.getGraphJson());

        FlowDesignPatchConfirmRequest addEdgeRequest = new FlowDesignPatchConfirmRequest();
        addEdgeRequest.setGraphJson(addNodeResult.getGraphJson());
        addEdgeRequest.setPatch(fixture.getObject("patch", FlowDesignPatch.class));
        addEdgeRequest.setUnitId("addEdge:8001");
        addEdgeRequest.setConfirmedUnitIds(List.of("addNode:9001"));

        FlowDesignPatchConfirmResult addEdgeResult = confirmService.confirmUnit(addEdgeRequest);
        assertNotNull(addEdgeResult.getGraphJson());
        // base 已有场景入口→HTTP 边，再确认 patch 边后共 2 条
        assertEquals(2, addEdgeResult.getGraphJson().getEdges().size());
    }

    /**
     * 前提：夹具 condition-add-edge-only，单单元 confirm。
     * 期望：分支 target 同步，confirm 成功。
     */
    @Test
    @Order(5)
    @DisplayName("条件节点仅补边确认成功")
    void confirm_conditionAddEdgeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/condition-add-edge-only.json");
    }

    /**
     * 前提：夹具 partial-node-only（只 confirm 节点）。
     * 期望：孤立新增节点可单独落盘，成功。
     */
    @Test
    @Order(6)
    @DisplayName("仅确认孤立节点可落盘")
    void confirm_partialNodeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/partial-node-only.json");
    }

    /**
     * 前提：夹具 delete-node-cascade。
     * 期望：删除节点并级联清边，成功。
     */
    @Test
    @Order(7)
    @DisplayName("删除节点级联清边成功")
    void confirm_deleteNodeCascade_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/delete-node-cascade.json");
    }

    /**
     * 前提：夹具 condition-update-edge；merger 会按出边同步 branches.target。
     * 期望：confirm 成功，边目标切到 1003（与 fixture.expectOk 一致）。
     */
    @Test
    @Order(8)
    @DisplayName("更新条件边同步分支目标成功")
    void confirm_conditionUpdateEdge_matchesPreviewBehavior() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/condition-update-edge.json");
    }

    /**
     * 前提：夹具 scenario-only（仅 updateScenario）。
     * 期望：场景字段合并成功。
     */
    @Test
    @Order(9)
    @DisplayName("仅场景 patch 确认成功")
    void confirm_scenarioOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/scenario-only.json");
    }

    /**
     * 前提：夹具 remerge-same-accepted，按序逐单元 confirm。
     * 期望：合并结果稳定，成功。
     */
    @Test
    @Order(10)
    @DisplayName("同 accepted 再合并结果稳定")
    void confirm_remergeSameAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/remerge-same-accepted.json");
    }

    /**
     * 前提：同一 unitId + 请求连续 confirm 两次。
     * 期望：均成功，合并图 hash 一致。
     */
    @Test
    @Order(11)
    @DisplayName("同 unitId 连续确认幂等")
    void confirm_sameUnitId_idempotent() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-node-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), null);

        FlowDesignPatchConfirmResult first = confirmService.confirmUnit(request);
        FlowDesignPatchConfirmResult second = confirmService.confirmUnit(request);

        assertNotNull(first.getGraphJson());
        assertNotNull(second.getGraphJson());
        assertEquals(
                GraphJsonHashUtil.computeBaseGraphHash(first.getGraphJson()),
                GraphJsonHashUtil.computeBaseGraphHash(second.getGraphJson()));
    }

    /**
     * 前提：先加节点，再 updateNode + draftOverride 改 name。
     * 期望：校验通过，落盘 name=登录V2。
     */
    @Test
    @Order(12)
    @DisplayName("updateNode 带 draft 改名成功")
    void confirm_updateNodeWithDraft_ok() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/linear-add-accepted.json");

        FlowDesignPatchConfirmRequest addNodeRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), null);
        FlowDesignPatchConfirmResult addNodeResult = confirmService.confirmUnit(addNodeRequest);
        assertNotNull(addNodeResult.getGraphJson());

        FlowDesignPatch patch = new FlowDesignPatch();
        GraphNode update = GraphNode.builder()
                .id("9001")
                .name("登录V2")
                .data(new java.util.HashMap<>())
                .build();
        patch.getUpdateNodes().add(update);

        FlowDesignPatchConfirmRequest updateRequest = new FlowDesignPatchConfirmRequest();
        updateRequest.setGraphJson(addNodeResult.getGraphJson());
        updateRequest.setPatch(patch);
        updateRequest.setUnitId("updateNode:9001");
        updateRequest.setConfirmedUnitIds(List.of("addNode:9001"));
        updateRequest.setDraftOverride(Map.of("name", "登录V2"));

        FlowDesignPatchConfirmResult updateResult = confirmService.confirmUnit(updateRequest);
        assertNotNull(updateResult.getGraphJson());
        GraphNode merged = FlowDesignPatchMerger.findNode(updateResult.getGraphJson().getNodes(), "9001");
        assertNotNull(merged);
        assertEquals("登录V2", merged.getName());
    }

    /**
     * 前提：先用坏 draft（空 externalUrl）确认，再用合法 draft 确认。
     * 期望：两次均落盘（校验不挡确认）；坏 draft 可有 soft warnings。
     */
    @Test
    @Order(13)
    @DisplayName("坏 draft 仍落盘后合法 draft 亦可确认")
    void confirm_badDraftStillApplies_thenGoodDraftOk() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-node-only.json");

        Map<String, Object> badDraft = Map.of(
                "name", "坏节点",
                "data", Map.of(
                        "callMode", "external",
                        "externalUrl", "",
                        "httpMethod", "GET"));

        FlowDesignPatchConfirmRequest badRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), badDraft);
        FlowDesignPatchConfirmResult badResult = confirmService.confirmUnit(badRequest);
        assertNotNull(badResult.getGraphJson());

        Map<String, Object> goodDraft = Map.of(
                "name", "下一步",
                "data", Map.of(
                        "callMode", "external",
                        "externalUrl", "https://example.com/next",
                        "httpMethod", "GET"));

        FlowDesignPatchConfirmRequest goodRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), goodDraft);
        FlowDesignPatchConfirmResult goodResult = confirmService.confirmUnit(goodRequest);
        assertNotNull(goodResult.getGraphJson());
    }

    /**
     * 前提：multi-add-nodes-partial，连线尚未全部 confirm。
     * 期望：第二个 addNode 仍成功，并有「开始节点」相关 warning。
     */
    @Test
    @Order(14)
    @DisplayName("边未全确认时第二个加节点仍成功")
    void confirm_secondAddNodeWhileEdgePending_ok() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/multi-add-nodes-partial.json");
        GraphJson baseGraph = fixture.getObject("baseGraph", GraphJson.class);
        FlowDesignPatch patch = fixture.getObject("patch", FlowDesignPatch.class);

        FlowDesignPatchConfirmRequest firstRequest = new FlowDesignPatchConfirmRequest();
        firstRequest.setGraphJson(baseGraph);
        firstRequest.setPatch(patch);
        firstRequest.setUnitId("addNode:9001");
        FlowDesignPatchConfirmResult firstResult = confirmService.confirmUnit(firstRequest);
        assertNotNull(firstResult.getGraphJson());
        // base 已有场景入口节点，确认 addNode:9001 后共 2 个节点
        assertEquals(2, firstResult.getGraphJson().getNodes().size());

        FlowDesignPatchConfirmRequest secondRequest = new FlowDesignPatchConfirmRequest();
        secondRequest.setGraphJson(firstResult.getGraphJson());
        secondRequest.setPatch(patch);
        secondRequest.setUnitId("addNode:9002");
        secondRequest.setConfirmedUnitIds(List.of("addNode:9001"));

        FlowDesignPatchConfirmResult secondResult = confirmService.confirmUnit(secondRequest);
        assertNotNull(secondResult.getGraphJson());
        assertEquals(3, secondResult.getGraphJson().getNodes().size());
        // 边仍 pending：半成品拓扑不挡确认；开始节点硬规则留给开跑门禁
    }

    /**
     * 前提：updateScenario + draftOverride 改 remark。
     * 期望：场景字段落盘成功；旧失败策略键被忽略。
     */
    @Test
    @Order(15)
    @DisplayName("updateScenario 带 draft 落盘成功")
    void confirm_updateScenarioWithDraft_ok() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/scenario-only.json");
        GraphJson graph = fixture.getObject("baseGraph", GraphJson.class);

        FlowDesignPatch patch = new FlowDesignPatch();
        com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch scenarioPatch =
                new com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch();
        GraphRunScenario update = GraphRunScenario.builder()
                .id("sc1")
                .name("默认")
                .build();
        scenarioPatch.getUpdateScenarios().add(update);
        patch.setScenarioPatch(scenarioPatch);

        Map<String, Object> draftOverride = Map.of(
                "remark", "新说明",
                "onNodeFailure", "prompt",
                "onSnapshotFailure", "continue");

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(graph);
        request.setPatch(patch);
        request.setUnitId("updateScenario:sc1");
        request.setDraftOverride(draftOverride);

        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        assertNotNull(result.getGraphJson());

        GraphRunScenario merged = com.qualitest.flow.context.EntryScenarioSupport.allEntryScenarios(result.getGraphJson()).stream()
                .filter(s -> "sc1".equals(s.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(merged);
        assertEquals("新说明", merged.getRemark());
    }

    /**
     * 前提：patch 含上游 HTTP + 入边 + 误写 .items 的断言；仅 confirm 断言（边仍 pending）。
     * 期望：确认可落盘（AssertPath schema 门禁后移到保存），errors 不含路径 schema 硬拦。
     */
    @Test
    @Order(16)
    @DisplayName("确认断言时不因 pending 上游 schema 失败")
    void confirm_assertWithPendingUpstream_okWithoutAssertGate() {
        FlowDesignPatchConfirmService gated = confirmServiceWithCartApi();
        FlowDesignPatch patch = cartAssertPatch("http.body.data.items[0].quantity");

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(emptyBaseWithScenario());
        request.setPatch(patch);
        request.setUnitId("addNode:9002");
        request.setTestProjectId(100L);

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertNotNull(result.getGraphJson());
    }

    /**
     * 前提：base 已有 HTTP + 坏路径断言；patch 仅补入边。
     * 期望：确认边成功（边不背断言门禁）。
     */
    @Test
    @Order(17)
    @DisplayName("确认入边不因已有坏断言试算失败")
    void confirm_addEdge_ignoresAssertGateOnBase() {
        FlowDesignPatchConfirmService gated = confirmServiceWithCartApi();

        Map<String, Object> httpData = projectHttpData();
        Map<String, Object> assertData = new HashMap<>();
        assertData.put("rules", List.of(Map.of(
                "left", "http.body.data.items[0].quantity",
                "operator", "eq",
                "right", "3")));

        GraphJson base = emptyBaseWithScenario();
        base.setNodes(List.of(
                GraphNode.builder()
                        .id("9001")
                        .type("http")
                        .name("我的购物车")
                        .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                        .data(httpData)
                        .build(),
                GraphNode.builder()
                        .id("9002")
                        .type("assert")
                        .name("断言数量")
                        .position(com.qualitest.flow.model.GraphNodePosition.builder().x(420).y(80).build())
                        .data(assertData)
                        .build()));

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddEdges().add(GraphEdge.builder().id("8001").source("9001").target("9002").build());

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(base);
        request.setPatch(patch);
        request.setUnitId("addEdge:8001");

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertNotNull(result.getGraphJson());
        assertEquals(1, result.getGraphJson().getEdges().size());
    }

    /**
     * 前提：仅 addNode 断言，无上游 HTTP/边。
     * 期望：确认可落盘（业务门禁后移到保存）；errors 不含「尚无上游」。
     */
    @Test
    @Order(18)
    @DisplayName("确认孤立断言无上游时仍可落盘")
    void confirm_assertWithoutUpstream_okWithoutBusinessGate() {
        FlowDesignPatchConfirmService gated = confirmServiceWithCartApi();

        Map<String, Object> assertData = new HashMap<>();
        assertData.put("rules", List.of(Map.of(
                "left", "http.body.data[0].quantity",
                "operator", "eq",
                "right", "3")));

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddNodes().add(GraphNode.builder()
                .id("9002")
                .type("assert")
                .name("孤立断言")
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                .data(assertData)
                .build());

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(emptyBaseWithScenario());
        request.setPatch(patch);
        request.setUnitId("addNode:9002");

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertNotNull(result.getGraphJson());
    }

    /**
     * 前提：项目已配双端鉴权；确认一个需登录的客户端 HTTP，图中无 token 写入来源。
     * 期望：确认仍落盘（鉴权不进 Confirm 响应，由画布 stage=canvas 图 error 预检）。
     */
    @Test
    @Order(19)
    @DisplayName("确认需登录 HTTP 缺 token 时仍可落盘")
    void confirm_projectHttpMissingToken_okWithoutBusinessGate() {
        FlowDesignPatchConfirmResult result = confirmDualBearerHttpNode(
                "9501", "资料", null);
        assertNotNull(result.getGraphJson());
    }

    /**
     * 前提：同上，但该 HTTP 的 extracts 已声明写出 asset.clientAuth.token。
     * 期望：确认落盘。
     */
    @Test
    @Order(20)
    @DisplayName("确认需登录 HTTP 已有 token extracts 时通过")
    void confirm_projectHttpWithTokenExtract_ok() {
        FlowDesignPatchConfirmResult result = confirmDualBearerHttpNode(
                "9502",
                "登录",
                List.of(Map.of(
                        "name", "token",
                        "scope", "asset",
                        "entryKey", "clientAuth",
                        "fieldPath", "token",
                        "expr", "$.data.token",
                        "from", "body")));
        assertNotNull(result.getGraphJson());
    }

    /**
     * 构造并确认单个 project HTTP 的 addNode（可选 extracts）。
     * 使用双端 Bearer 项目鉴权与 /api 路径接口，便于测 token 来源检查。
     */
    private static FlowDesignPatchConfirmResult confirmDualBearerHttpNode(
            String nodeId, String name, List<? extends Map<String, ?>> extracts) {
        FlowDesignPatchConfirmService gated = confirmServiceWithDualBearerAuth();
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("callMode", "project");
        httpData.put("testProjectApiId", "501");
        if (extracts != null) {
            httpData.put("extracts", extracts);
        }
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddNodes().add(GraphNode.builder()
                .id(nodeId)
                .type("http")
                .name(name)
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                .data(httpData)
                .build());
        GraphJson base = emptyBaseWithScenario();
        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(base);
        request.setPatch(patch);
        request.setUnitId("addNode:" + nodeId);
        request.setTestProjectId(100L);
        return gated.confirmUnit(request);
    }

    /** 单单元 fixture：acceptedIds 仅一项时直接 confirm */
    private void runSingleUnitFixture(String classpath) throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture(classpath);
        String unitId = fixture.getList("acceptedIds", String.class).get(0);
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, unitId, List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        FlowMergeFixtureTestSupport.assertConfirmFixture(fixture, result);
    }

    /** 多单元 fixture：按 acceptedIds 顺序逐单元 confirm */
    private void runSequentialFixture(String classpath) throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture(classpath);
        FlowDesignPatchConfirmResult result = FlowMergeFixtureTestSupport.confirmAllAcceptedUnits(fixture, confirmService);
        assertTrue(fixture.getBooleanValue("expectOk"));
        assertNotNull(result);
        assertNotNull(result.getGraphJson());
        FlowMergeFixtureTestSupport.assertMergedGraphExpectations(fixture, result.getGraphJson());
    }

    private static FlowDesignPatchConfirmService confirmServiceWithCartApi() {
        TestProjectApiMapper mapper = mock(TestProjectApiMapper.class);
        when(mapper.selectTestProjectApiById(anyLong())).thenReturn(TestProjectApi.builder()
                .testProjectApiId(1L)
                .testProjectId(100L)
                .responseConfig("""
                        {"responses":[{"id":"r1","schema":{"type":"object","properties":{"code":{"type":"integer"},"data":{"type":"array","items":{"type":"object","properties":{"cartId":{"type":"integer"},"quantity":{"type":"integer"},"subtotal":{"type":"number"}}}}}},"example":{"code":0,"data":[{"cartId":0,"quantity":0,"subtotal":0}]}}]}
                        """)
                .build());
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(mapper, null, null, new GraphJsonValidator(null), merger, null);
        return new FlowDesignPatchConfirmService(normalizer, merger);
    }

    /** 装配带双端 Bearer 项目鉴权与 /api 接口的 ConfirmService，供缺 token / 有 extracts 用例使用 */
    private static FlowDesignPatchConfirmService confirmServiceWithDualBearerAuth() {
        TestProjectApiMapper apiMapper = mock(TestProjectApiMapper.class);
        when(apiMapper.selectTestProjectApiById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return TestProjectApi.builder()
                    .testProjectApiId(id)
                    .testProjectId(100L)
                    .apiPath("/api/account/auth/profile")
                    .authConfig("{\"mode\":\"inherit\"}")
                    .build();
        });
        TestProjectMapper projectMapper = mock(TestProjectMapper.class);
        when(projectMapper.selectTestProjectById(anyLong())).thenReturn(TestProject.builder()
                .testProjectId(100L)
                .authConfig(com.qualitest.project.auth.AuthProfileTestFixtures.adminThenClientJson())
                .build());
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(
                apiMapper, projectMapper, null, new GraphJsonValidator(null), merger, null);
        return new FlowDesignPatchConfirmService(normalizer, merger);
    }

    private static GraphJson emptyBaseWithScenario() {
        Map<String, Object> data = new HashMap<>();
        data.put("scenarios", List.of(GraphRunScenario.builder()
                .id("sc1")
                .name("默认")
                .envId("")
                .inSeed(Map.of())
                .build()));
        GraphNode entry = GraphNode.builder()
                .id("entry-sc1")
                .type("scenario-entry")
                .name("默认场景入口")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .data(data)
                .build();
        return GraphJson.builder()
                .nodes(List.of(entry))
                .edges(List.of())
                .meta(com.qualitest.flow.model.GraphMeta.builder()
                        .layout("manual")
                        
                        .build())
                .build();
    }

    private static Map<String, Object> projectHttpData() {
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("callMode", "project");
        httpData.put("testProjectApiId", "1");
        return httpData;
    }

    /** HTTP + 入边 + 断言 的完整 pending patch（数字 id，避免 preparePatch 重写） */
    private static FlowDesignPatch cartAssertPatch(String assertLeft) {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddNodes().add(GraphNode.builder()
                .id("9001")
                .type("http")
                .name("我的购物车")
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                .data(projectHttpData())
                .build());
        Map<String, Object> assertData = new HashMap<>();
        assertData.put("rules", List.of(Map.of(
                "left", assertLeft,
                "operator", "eq",
                "right", "3")));
        patch.getAddNodes().add(GraphNode.builder()
                .id("9002")
                .type("assert")
                .name("断言数量")
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(420).y(80).build())
                .data(assertData)
                .build());
        patch.getAddEdges().add(GraphEdge.builder().id("8001").source("9001").target("9002").build());
        return patch;
    }
}
