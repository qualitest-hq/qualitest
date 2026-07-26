package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.GraphJsonValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI patch 合并器单元测试。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowDesignPatchMergerTest
 */
class FlowDesignPatchMergerTest {

    private FlowDesignPatchMerger merger;
    private FlowDesignPatchNormalizer normalizer;
    private GraphJsonValidator validator;

    @BeforeEach
    void setUp() {
        merger = new FlowDesignPatchMerger();
        validator = new GraphJsonValidator();
        normalizer = new FlowDesignPatchNormalizer(null, validator, merger);
    }

    @Test
    void merge_fixture_linearAddAccepted() throws IOException {
        runFixture("flow/merge-fixtures/linear-add-accepted.json");
    }

    @Test
    void merge_fixture_partialEdgeOnly_fails() throws IOException {
        runFixture("flow/merge-fixtures/partial-edge-only.json");
    }

    @Test
    void merge_fixture_conditionAddEdgeOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/condition-add-edge-only.json");
    }

    @Test
    void merge_fixture_partialNodeOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/partial-node-only.json");
    }

    @Test
    void merge_fixture_deleteNodeCascade_ok() throws IOException {
        runFixture("flow/merge-fixtures/delete-node-cascade.json");
    }

    @Test
    void merge_fixture_conditionUpdateEdge_ok() throws IOException {
        runFixture("flow/merge-fixtures/condition-update-edge.json");
    }

    @Test
    void merge_fixture_scenarioOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/scenario-only.json");
    }

    @Test
    void merge_fixture_remergeSameAccepted_ok() throws IOException {
        runFixture("flow/merge-fixtures/remerge-same-accepted.json");
    }

    @Test
    void filterPatchByAccepted_onlyIncludesCheckedItems() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatch patch = fixture.getObject("patch", FlowDesignPatch.class);
        Set<String> accepted = new HashSet<>(fixture.getList("acceptedIds", String.class));

        FlowDesignPatch filtered = merger.filterPatchByAccepted(patch, accepted);

        assertTrue(filtered.getAddNodes().isEmpty());
        assertEquals(1, filtered.getAddEdges().size());
        assertEquals("8001", filtered.getAddEdges().get(0).getId());
    }

    @Test
    void filterPatchByAccepted_scenarioActiveScenarioId() {
        FlowDesignPatch patch = new FlowDesignPatch();
        FlowDesignScenarioPatch scenarioPatch = new FlowDesignScenarioPatch();
        scenarioPatch.setActiveScenarioId("sc2");
        patch.setScenarioPatch(scenarioPatch);

        FlowDesignPatch filtered = merger.filterPatchByAccepted(
                patch,
                Set.of("scenario:activeScenarioId"));

        assertEquals("sc2", filtered.getScenarioPatch().getActiveScenarioId());
    }

    @Test
    void applyNodeUpdate_mergesData() {
        GraphNode existing = GraphNode.builder()
                .id("1001")
                .type("http")
                .data(new java.util.HashMap<>(Map.of("name", "旧名")))
                .build();
        GraphNode update = GraphNode.builder()
                .id("1001")
                .data(new java.util.HashMap<>(Map.of("name", "新名", "callMode", "external")))
                .build();

        FlowDesignPatchMerger.applyNodeUpdate(existing, update);

        assertEquals("新名", existing.getData().get("name"));
        assertEquals("external", existing.getData().get("callMode"));
    }

    @Test
    void deleteNode_cascadesEdges() {
        GraphJson base = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(
                        GraphNode.builder().id("1001").type("start").build(),
                        GraphNode.builder().id("1002").type("http").build()
                )))
                .edges(new ArrayList<>(List.of(
                        GraphEdge.builder().id("8001").source("1001").target("1002").build()
                )))
                .meta(GraphMeta.builder().build())
                .build();

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        patch.getSuggestedDeletes().getNodeIds().add("1002");

        GraphJson merged = merger.merge(base, patch, Set.of("deleteNode:1002"), new ArrayList<>());

        assertNull(GraphLookupUtils.findNode(merged.getNodes(), "1002"));
        assertTrue(merged.getEdges().isEmpty());
    }

    private void runFixture(String classpath) throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture(classpath);
        GraphJson base = fixture.getObject("baseGraph", GraphJson.class);
        FlowDesignPatch patch = JSON.parseObject(
                JSON.toJSONString(fixture.getObject("patch", FlowDesignPatch.class)),
                FlowDesignPatch.class);
        Set<String> accepted = new HashSet<>(fixture.getList("acceptedIds", String.class));
        List<String> warnings = new ArrayList<>();
        normalizer.preparePatch(patch, base, null, warnings);
        GraphJson merged = merger.merge(base, patch, accepted, warnings);

        boolean expectOk = fixture.getBooleanValue("expectOk");
        assertNotNull(merged);
        if (expectOk) {
            FlowMergeFixtureTestSupport.assertMergedGraphExpectations(fixture, merged);
        } else {
            var validation = validator.validate(merged);
            assertFalse(validation.isOk(), () -> "errors=" + validation.getErrors());
        }
    }
}
