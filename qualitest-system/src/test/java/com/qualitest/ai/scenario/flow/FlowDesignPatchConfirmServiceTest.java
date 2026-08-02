package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignPatchConfirmService：单 Staging 单元 confirm（合并、依赖、图校验、幂等）。
 * 边界：全部内存完成，不写数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignPatchConfirmServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignPatchConfirmServiceTest {

    private FlowDesignPatchConfirmService confirmService;

    @BeforeEach
    void setUp() {
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(null, new GraphJsonValidator(), merger);
        confirmService = new FlowDesignPatchConfirmService(normalizer, merger, new GraphJsonValidator(), null);
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
     * 期望：失败（与夹具 assertConfirmFixture 一致）。
     */
    @Test
    @Order(2)
    @DisplayName("仅确认边缺依赖节点时失败")
    void confirm_partialEdgeOnly_fails() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addEdge:8001", List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        FlowMergeFixtureTestSupport.assertConfirmFixture(fixture, result);
    }

    /**
     * 前提：同上，仅 confirm addEdge。
     * 期望：dependencyHints 提示需先确认 addNode:9001；baseGraphHash 长 16；无 graphJson。
     */
    @Test
    @Order(3)
    @DisplayName("仅确认边时返回依赖提示")
    void confirm_partialEdgeOnly_emitsDependencyHints() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addEdge:8001", List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        assertFalse(result.getDependencyHints().isEmpty());
        assertEquals(
                "确认 addEdge:8001 需要先确认 addNode:9001",
                result.getDependencyHints().get(0));
        assertTrue(result.getBaseGraphHash().length() == 16);
        assertNull(result.getGraphJson());
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
        assertTrue(addNodeResult.isOk(), () -> "errors=" + addNodeResult.getErrors());

        FlowDesignPatchConfirmRequest addEdgeRequest = new FlowDesignPatchConfirmRequest();
        addEdgeRequest.setGraphJson(addNodeResult.getGraphJson());
        addEdgeRequest.setPatch(fixture.getObject("patch", FlowDesignPatch.class));
        addEdgeRequest.setUnitId("addEdge:8001");
        addEdgeRequest.setConfirmedUnitIds(List.of("addNode:9001"));

        FlowDesignPatchConfirmResult addEdgeResult = confirmService.confirmUnit(addEdgeRequest);
        assertTrue(addEdgeResult.isOk(), () -> "errors=" + addEdgeResult.getErrors());
        assertEquals(1, addEdgeResult.getGraphJson().getEdges().size());
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
     * 前提：夹具 condition-update-edge（与 preview 同 fixture）。
     * 期望：当前校验器对旧目标悬空报错，confirm 失败且无 graphJson。
     */
    @Test
    @Order(8)
    @DisplayName("更新条件边与 preview 同失败")
    void confirm_conditionUpdateEdge_matchesPreviewBehavior() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/condition-update-edge.json");
        String unitId = fixture.getList("acceptedIds", String.class).get(0);
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, unitId, List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        assertFalse(result.isOk());
        assertNull(result.getGraphJson());
    }

    /**
     * 前提：夹具 scenario-only。
     * 期望：仅切换默认场景，成功。
     */
    @Test
    @Order(9)
    @DisplayName("仅切换默认场景成功")
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

        assertTrue(first.isOk());
        assertTrue(second.isOk());
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
        assertTrue(addNodeResult.isOk());

        FlowDesignPatch patch = new FlowDesignPatch();
        GraphNode update = GraphNode.builder()
                .id("9001")
                .data(new java.util.HashMap<>(Map.of("name", "登录V2")))
                .build();
        patch.getUpdateNodes().add(update);

        FlowDesignPatchConfirmRequest updateRequest = new FlowDesignPatchConfirmRequest();
        updateRequest.setGraphJson(addNodeResult.getGraphJson());
        updateRequest.setPatch(patch);
        updateRequest.setUnitId("updateNode:9001");
        updateRequest.setConfirmedUnitIds(List.of("addNode:9001"));
        updateRequest.setDraftOverride(Map.of("data", Map.of("name", "登录V2")));

        FlowDesignPatchConfirmResult updateResult = confirmService.confirmUnit(updateRequest);
        assertTrue(updateResult.isOk(), () -> "errors=" + updateResult.getErrors());
        GraphNode merged = FlowDesignPatchMerger.findNode(updateResult.getGraphJson().getNodes(), "9001");
        assertNotNull(merged);
        assertEquals("登录V2", merged.getData().get("name"));
    }

    /**
     * 前提：先用坏 draft（空 externalUrl）失败，再用合法 draft 重试。
     * 期望：先 false，后 true 且有 graphJson。
     */
    @Test
    @Order(13)
    @DisplayName("坏 draft 失败后合法 draft 重试成功")
    void confirm_failedRetryWithDraft_ok() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-node-only.json");

        Map<String, Object> badDraft = Map.of(
                "data", Map.of(
                        "name", "坏节点",
                        "callMode", "external",
                        "externalUrl", "",
                        "httpMethod", "GET"));

        FlowDesignPatchConfirmRequest badRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), badDraft);
        FlowDesignPatchConfirmResult badResult = confirmService.confirmUnit(badRequest);
        assertFalse(badResult.isOk());
        assertNull(badResult.getGraphJson());
        assertFalse(badResult.getErrors().isEmpty());

        Map<String, Object> goodDraft = Map.of(
                "data", Map.of(
                        "name", "下一步",
                        "callMode", "external",
                        "externalUrl", "https://example.com/next",
                        "httpMethod", "GET"));

        FlowDesignPatchConfirmRequest goodRequest = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addNode:9001", List.of(), goodDraft);
        FlowDesignPatchConfirmResult goodResult = confirmService.confirmUnit(goodRequest);
        assertTrue(goodResult.isOk(), () -> "errors=" + goodResult.getErrors());
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
        assertTrue(firstResult.isOk(), () -> "errors=" + firstResult.getErrors());
        assertEquals(1, firstResult.getGraphJson().getNodes().size());

        FlowDesignPatchConfirmRequest secondRequest = new FlowDesignPatchConfirmRequest();
        secondRequest.setGraphJson(firstResult.getGraphJson());
        secondRequest.setPatch(patch);
        secondRequest.setUnitId("addNode:9002");
        secondRequest.setConfirmedUnitIds(List.of("addNode:9001"));

        FlowDesignPatchConfirmResult secondResult = confirmService.confirmUnit(secondRequest);
        assertTrue(secondResult.isOk(), () -> "errors=" + secondResult.getErrors());
        assertEquals(2, secondResult.getGraphJson().getNodes().size());
        assertTrue(secondResult.getWarnings().stream().anyMatch(w -> w.contains("开始节点")));
    }

    /**
     * 前提：updateScenario + draftOverride 改 remark / 失败策略。
     * 期望：场景字段落盘成功。
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
        assertTrue(result.isOk(), () -> "errors=" + result.getErrors());
        assertNotNull(result.getGraphJson());

        GraphRunScenario merged = result.getGraphJson().getMeta().getScenarios().stream()
                .filter(s -> "sc1".equals(s.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(merged);
        assertEquals("新说明", merged.getRemark());
        assertEquals("prompt", merged.getOnNodeFailure());
        assertEquals("continue", merged.getOnSnapshotFailure());
    }

    /**
     * 前提：patch 含上游 HTTP + 入边 + 误写 .items 的断言；仅 confirm 断言（边仍 pending）。
     * 期望：预览图含 pending 上游，schema 门禁失败，错误归属断言。
     */
    @Test
    @Order(16)
    @DisplayName("确认断言时 pending 上游参与 schema 校验且失败归属断言")
    void confirm_assertWithPendingUpstream_failsOnSchemaItems() {
        FlowDesignPatchConfirmService gated = confirmServiceWithCartApi();
        FlowDesignPatch patch = cartAssertPatch("http.body.data.items[0].quantity");

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(emptyBaseWithScenario());
        request.setPatch(patch);
        request.setUnitId("addNode:9002");
        request.setTestProjectId(100L);

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains(".items")),
                () -> "errors=" + result.getErrors());
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
        assertData.put("name", "断言数量");
        assertData.put("rules", List.of(Map.of(
                "left", "http.body.data.items[0].quantity",
                "operator", "eq",
                "right", "3")));

        GraphJson base = emptyBaseWithScenario();
        base.setNodes(List.of(
                GraphNode.builder()
                        .id("9001")
                        .type("http")
                        .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                        .data(httpData)
                        .build(),
                GraphNode.builder()
                        .id("9002")
                        .type("assert")
                        .position(com.qualitest.flow.model.GraphNodePosition.builder().x(420).y(80).build())
                        .data(assertData)
                        .build()));
        base.getMeta().setStartNodeId("9001");

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddEdges().add(GraphEdge.builder().id("8001").source("9001").target("9002").build());

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(base);
        request.setPatch(patch);
        request.setUnitId("addEdge:8001");

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertTrue(result.isOk(), () -> "errors=" + result.getErrors());
        assertEquals(1, result.getGraphJson().getEdges().size());
    }

    /**
     * 前提：仅 addNode 断言，无上游 HTTP/边。
     * 期望：confirm 失败，错误含「尚无上游」。
     */
    @Test
    @Order(18)
    @DisplayName("确认孤立断言无上游时硬拦")
    void confirm_assertWithoutUpstream_failsWithHint() {
        FlowDesignPatchConfirmService gated = confirmServiceWithCartApi();

        Map<String, Object> assertData = new HashMap<>();
        assertData.put("name", "孤立断言");
        assertData.put("rules", List.of(Map.of(
                "left", "http.body.data[0].quantity",
                "operator", "eq",
                "right", "3")));

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddNodes().add(GraphNode.builder()
                .id("9002")
                .type("assert")
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                .data(assertData)
                .build());

        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(emptyBaseWithScenario());
        request.setPatch(patch);
        request.setUnitId("addNode:9002");

        FlowDesignPatchConfirmResult result = gated.confirmUnit(request);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("尚无上游")),
                () -> "errors=" + result.getErrors());
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
        assertTrue(result.isOk());
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
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(mapper, new GraphJsonValidator(), merger);
        return new FlowDesignPatchConfirmService(normalizer, merger, new GraphJsonValidator(), mapper);
    }

    private static GraphJson emptyBaseWithScenario() {
        return GraphJson.builder()
                .nodes(List.of())
                .edges(List.of())
                .meta(com.qualitest.flow.model.GraphMeta.builder()
                        .layout("manual")
                        .activeScenarioId("sc1")
                        .scenarios(List.of(GraphRunScenario.builder()
                                .id("sc1")
                                .name("默认")
                                .testProjectEnvId("")
                                .flowSeed(Map.of())
                                .build()))
                        .flowOutputs(List.of())
                        .build())
                .build();
    }

    private static Map<String, Object> projectHttpData() {
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("name", "我的购物车");
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
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(40).y(80).build())
                .data(projectHttpData())
                .build());
        Map<String, Object> assertData = new HashMap<>();
        assertData.put("name", "断言数量");
        assertData.put("rules", List.of(Map.of(
                "left", assertLeft,
                "operator", "eq",
                "right", "3")));
        patch.getAddNodes().add(GraphNode.builder()
                .id("9002")
                .type("assert")
                .position(com.qualitest.flow.model.GraphNodePosition.builder().x(420).y(80).build())
                .data(assertData)
                .build());
        patch.getAddEdges().add(GraphEdge.builder().id("8001").source("9001").target("9002").build());
        return patch;
    }
}
