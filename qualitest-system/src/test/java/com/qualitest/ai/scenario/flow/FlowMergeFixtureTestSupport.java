package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.GraphJsonValidator;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI patch 合并与 confirm 单元测试的公共辅助类。
 */
final class FlowMergeFixtureTestSupport {

    private FlowMergeFixtureTestSupport() {
    }

    static JSONObject loadFixture(String classpath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return JSON.parseObject(json);
    }

    /**
     * 模拟前端已将 patch 全部 Staging 项注入画布后的 graph_json。
     */
    static GraphJson buildStagingCanvas(JSONObject fixture) {
        FlowDesignPatchMerger merger = new FlowDesignPatchMerger();
        FlowDesignPatchNormalizer normalizer = new FlowDesignPatchNormalizer(null, null, new GraphJsonValidator(), merger);
        GraphJson base = fixture.getObject("baseGraph", GraphJson.class);
        FlowDesignPatch patch = JSON.parseObject(
                JSON.toJSONString(fixture.getObject("patch", FlowDesignPatch.class)),
                FlowDesignPatch.class);
        List<String> warnings = new ArrayList<>();
        normalizer.preparePatch(patch, base, null, warnings);
        return merger.mergeAll(base, patch, new ArrayList<>());
    }

    static FlowDesignPatchConfirmRequest buildConfirmRequest(
            JSONObject fixture,
            String unitId,
            List<String> confirmedUnitIds,
            Object draftOverride) {
        FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
        request.setGraphJson(buildStagingCanvas(fixture));
        request.setPatch(fixture.getObject("patch", FlowDesignPatch.class));
        request.setUnitId(unitId);
        request.setConfirmedUnitIds(confirmedUnitIds != null ? confirmedUnitIds : List.of());
        request.setDraftOverride(draftOverride);
        return request;
    }

    /**
     * 按 fixture acceptedIds 顺序逐单元 confirm，模拟 Staging 逐个确认流程。
     */
    static FlowDesignPatchConfirmResult confirmAllAcceptedUnits(
            JSONObject fixture,
            FlowDesignPatchConfirmService confirmService) {
        List<String> acceptedIds = fixture.getList("acceptedIds", String.class);
        List<String> confirmed = new ArrayList<>();
        GraphJson currentGraph = buildStagingCanvas(fixture);
        FlowDesignPatchConfirmResult lastResult = null;

        for (String unitId : acceptedIds) {
            FlowDesignPatchConfirmRequest request = new FlowDesignPatchConfirmRequest();
            request.setGraphJson(currentGraph);
            request.setPatch(fixture.getObject("patch", FlowDesignPatch.class));
            request.setUnitId(unitId);
            request.setConfirmedUnitIds(new ArrayList<>(confirmed));

            FlowDesignPatchConfirmResult stepResult = confirmService.confirmUnit(request);
            lastResult = stepResult;
            boolean expectOk = fixture.getBooleanValue("expectOk");
            assertEquals(expectOk, stepResult.isOk(), () -> "unitId=" + unitId + " errors=" + stepResult.getErrors());

            if (!stepResult.isOk()) {
                return stepResult;
            }
            currentGraph = stepResult.getGraphJson();
            confirmed.add(unitId);
        }
        return lastResult;
    }

    static void assertConfirmFixture(JSONObject fixture, FlowDesignPatchConfirmResult result) {
        boolean expectOk = fixture.getBooleanValue("expectOk");
        assertEquals(expectOk, result.isOk(), () -> "errors=" + result.getErrors());
        assertNotNull(result.getBaseGraphHash());
        assertEquals(16, result.getBaseGraphHash().length());
        assertEquals(
                GraphJsonHashUtil.computeBaseGraphHash(buildStagingCanvas(fixture)),
                result.getBaseGraphHash());
        if (!expectOk) {
            assertFalse(result.getErrors().isEmpty());
            assertNull(result.getGraphJson());
        } else {
            assertNotNull(result.getGraphJson());
            assertMergedGraphExpectations(fixture, result.getGraphJson());
        }
    }

    static void assertMergedGraphExpectations(JSONObject fixture, GraphJson merged) {
        if (fixture.containsKey("expectEdgeCount")) {
            assertEquals(fixture.getIntValue("expectEdgeCount"), merged.getEdges().size());
        }
        if (fixture.containsKey("expectNodeAbsent")) {
            assertNull(GraphLookupUtils.findNode(merged.getNodes(), fixture.getString("expectNodeAbsent")));
        }
        if (fixture.containsKey("expectEdgeTarget")) {
            String edgeId = fixture.getString("expectEdgeId");
            if (edgeId == null || edgeId.isBlank()) {
                edgeId = "2001";
            }
            var edge = GraphLookupUtils.findEdge(merged.getEdges(), edgeId);
            assertNotNull(edge);
            assertEquals(fixture.getString("expectEdgeTarget"), edge.getTarget());
        }
        if (fixture.containsKey("expectActiveScenarioId")) {
            assertNotNull(merged.getMeta());
            assertEquals(fixture.getString("expectActiveScenarioId"), merged.getMeta().getActiveScenarioId());
        }
    }
}
