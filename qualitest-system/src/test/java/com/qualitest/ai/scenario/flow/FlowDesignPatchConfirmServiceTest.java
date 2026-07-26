package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.GraphJsonValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI patch 单 Staging 单元 confirm 服务单元测试。
 *
 * 验证 draft 合并、单 unitId 过滤、依赖校验、内存合并、图校验的完整 confirm 链路，
 * 以及 baseGraphHash、dependencyHints、失败重试与幂等等 Web 端依赖的响应字段。
 * 全部在内存中完成，不写数据库。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowDesignPatchConfirmServiceTest
 */
class FlowDesignPatchConfirmServiceTest {

    private FlowDesignPatchConfirmService confirmService;

    @BeforeEach
    void setUp() {
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(null, new GraphJsonValidator(), merger);
        confirmService = new FlowDesignPatchConfirmService(normalizer, merger, new GraphJsonValidator());
    }

    /** 线性新增：按 acceptedIds 顺序逐单元 confirm，应全部成功 */
    @Test
    void confirm_linearAddAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/linear-add-accepted.json");
    }

    /** 仅 confirm addEdge 不先 confirm addNode，依赖未满足应失败 */
    @Test
    void confirm_partialEdgeOnly_fails() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, "addEdge:8001", List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        FlowMergeFixtureTestSupport.assertConfirmFixture(fixture, result);
    }

    /** 仅 confirm addEdge 时，应携带依赖提示且 baseGraphHash 为 16 位 hex */
    @Test
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

    /** addEdge 依赖重试：先 confirm addNode，再 confirm addEdge 应成功 */
    @Test
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

    /** condition 节点画布：仅 confirm addEdge，分支 target 同步，应成功 */
    @Test
    void confirm_conditionAddEdgeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/condition-add-edge-only.json");
    }

    /** 只 confirm addNode 不 confirm addEdge，孤立新增节点可单独落盘，应成功 */
    @Test
    void confirm_partialNodeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/partial-node-only.json");
    }

    /** confirm deleteNode，合并时级联删除关联边，应成功 */
    @Test
    void confirm_deleteNodeCascade_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/delete-node-cascade.json");
    }

    /** confirm updateEdge：与 preview 同 fixture，当前图校验器对「旧目标悬空」报 error，保留用例对齐 */
    @Test
    void confirm_conditionUpdateEdge_matchesPreviewBehavior() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/condition-update-edge.json");
        String unitId = fixture.getList("acceptedIds", String.class).get(0);
        FlowDesignPatchConfirmRequest request = FlowMergeFixtureTestSupport.buildConfirmRequest(
                fixture, unitId, List.of(), null);
        FlowDesignPatchConfirmResult result = confirmService.confirmUnit(request);
        assertFalse(result.isOk());
        assertNull(result.getGraphJson());
    }

    /** 仅 confirm 默认场景切换，不修改节点与边，应成功 */
    @Test
    void confirm_scenarioOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/scenario-only.json");
    }

    /** 相同 acceptedIds 顺序逐单元 confirm，合并结果应稳定，应成功 */
    @Test
    void confirm_remergeSameAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/remerge-same-accepted.json");
    }

    /** 相同 unitId + draftOverride 重复 confirm，合并图 hash 应一致（幂等） */
    @Test
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

    /** confirm updateNode + draftOverride：draft 写入 data，校验通过后落盘 */
    @Test
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

    /** confirm 失败重试：draft 修正后 ok 由 false 变 true */
    @Test
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

    /** 两个 addNode 未 confirm 全部连线前，第二个 addNode 确认应成功（延后开始节点校验） */
    @Test
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

    /** confirm updateScenario + draftOverride：remark 与失败策略字段应落盘 */
    @Test
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
}
