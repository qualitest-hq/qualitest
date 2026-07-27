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
 * 测 FlowDesignPatchConfirmService：单 Staging 单元 confirm（合并、依赖、图校验、幂等）。
 * 边界：全部内存完成，不写数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignPatchConfirmServiceTest
 */
class FlowDesignPatchConfirmServiceTest {

    private FlowDesignPatchConfirmService confirmService;

    @BeforeEach
    void setUp() {
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(null, new GraphJsonValidator(), merger);
        confirmService = new FlowDesignPatchConfirmService(normalizer, merger, new GraphJsonValidator());
    }

    /**
     * 前提：夹具 linear-add-accepted，按 acceptedIds 顺序逐单元 confirm。
     * 期望：全部成功，合并图符合夹具。
     */
    @Test
    void confirm_linearAddAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/linear-add-accepted.json");
    }

    /**
     * 前提：仅 confirm addEdge:8001，未先 confirm 依赖节点。
     * 期望：失败（与夹具 assertConfirmFixture 一致）。
     */
    @Test
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
    void confirm_conditionAddEdgeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/condition-add-edge-only.json");
    }

    /**
     * 前提：夹具 partial-node-only（只 confirm 节点）。
     * 期望：孤立新增节点可单独落盘，成功。
     */
    @Test
    void confirm_partialNodeOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/partial-node-only.json");
    }

    /**
     * 前提：夹具 delete-node-cascade。
     * 期望：删除节点并级联清边，成功。
     */
    @Test
    void confirm_deleteNodeCascade_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/delete-node-cascade.json");
    }

    /**
     * 前提：夹具 condition-update-edge（与 preview 同 fixture）。
     * 期望：当前校验器对旧目标悬空报错，confirm 失败且无 graphJson。
     */
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

    /**
     * 前提：夹具 scenario-only。
     * 期望：仅切换默认场景，成功。
     */
    @Test
    void confirm_scenarioOnly_ok() throws IOException {
        runSingleUnitFixture("flow/merge-fixtures/scenario-only.json");
    }

    /**
     * 前提：夹具 remerge-same-accepted，按序逐单元 confirm。
     * 期望：合并结果稳定，成功。
     */
    @Test
    void confirm_remergeSameAccepted_ok() throws IOException {
        runSequentialFixture("flow/merge-fixtures/remerge-same-accepted.json");
    }

    /**
     * 前提：同一 unitId + 请求连续 confirm 两次。
     * 期望：均成功，合并图 hash 一致。
     */
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

    /**
     * 前提：先加节点，再 updateNode + draftOverride 改 name。
     * 期望：校验通过，落盘 name=登录V2。
     */
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

    /**
     * 前提：先用坏 draft（空 externalUrl）失败，再用合法 draft 重试。
     * 期望：先 false，后 true 且有 graphJson。
     */
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

    /**
     * 前提：multi-add-nodes-partial，连线尚未全部 confirm。
     * 期望：第二个 addNode 仍成功，并有「开始节点」相关 warning。
     */
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

    /**
     * 前提：updateScenario + draftOverride 改 remark / 失败策略。
     * 期望：场景字段落盘成功。
     */
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
