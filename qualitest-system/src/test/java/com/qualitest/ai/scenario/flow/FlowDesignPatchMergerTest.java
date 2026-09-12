package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.GraphJsonValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

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
 * 测 FlowDesignPatchMerger：AI patch 合并进图（节点/边/场景）与按 accepted 过滤。
 * 边界：内存合并 + GraphJsonValidator；数据驱动夹具在 flow/merge-fixtures/。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignPatchMergerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignPatchMergerTest {

    private FlowDesignPatchMerger merger;
    private FlowDesignPatchNormalizer normalizer;
    private GraphJsonValidator validator;

    @BeforeEach
    void setUp() {
        merger = new FlowDesignPatchMerger();
        validator = new GraphJsonValidator();
        normalizer = new FlowDesignPatchNormalizer(null, null, null, validator, merger);
    }

    /**
     * 前提：夹具 linear-add-accepted（线性加节点/边且全部 accepted）。
     * 期望：合并后图结构符合夹具断言，校验通过。
     */
    @Test
    @Order(1)
    @DisplayName("线性加节点边全部接受合并成功")
    void merge_fixture_linearAddAccepted() throws IOException {
        runFixture("flow/merge-fixtures/linear-add-accepted.json");
    }

    /**
     * 前提：夹具 partial-edge-only（只接受边、未接受对应节点）。
     * 期望：合并结果图校验失败。
     */
    @Test
    @Order(2)
    @DisplayName("只接受边未接受节点时校验失败")
    void merge_fixture_partialEdgeOnly_fails() throws IOException {
        runFixture("flow/merge-fixtures/partial-edge-only.json");
    }

    /**
     * 前提：夹具 condition-add-edge-only（仅给 condition 补出边）。
     * 期望：合并成功并通过夹具断言。
     */
    @Test
    @Order(3)
    @DisplayName("条件节点仅补边合并成功")
    void merge_fixture_conditionAddEdgeOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/condition-add-edge-only.json");
    }

    /**
     * 前提：夹具 partial-node-only（只接受节点）。
     * 期望：合并成功并通过夹具断言。
     */
    @Test
    @Order(4)
    @DisplayName("只接受节点合并成功")
    void merge_fixture_partialNodeOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/partial-node-only.json");
    }

    /**
     * 前提：夹具 delete-node-cascade（删除节点）。
     * 期望：节点删除且关联边被级联清理。
     */
    @Test
    @Order(5)
    @DisplayName("删除节点级联清理关联边")
    void merge_fixture_deleteNodeCascade_ok() throws IOException {
        runFixture("flow/merge-fixtures/delete-node-cascade.json");
    }

    /**
     * 前提：夹具 condition-update-edge（更新 condition 出边）。
     * 期望：合并成功并通过夹具断言。
     */
    @Test
    @Order(6)
    @DisplayName("更新条件出边合并成功")
    void merge_fixture_conditionUpdateEdge_ok() throws IOException {
        runFixture("flow/merge-fixtures/condition-update-edge.json");
    }

    /**
     * 前提：夹具 scenario-only（仅场景 patch）。
     * 期望：合并成功并通过夹具断言。
     */
    @Test
    @Order(7)
    @DisplayName("仅场景 patch 合并成功")
    void merge_fixture_scenarioOnly_ok() throws IOException {
        runFixture("flow/merge-fixtures/scenario-only.json");
    }

    /**
     * 前提：夹具 remerge-same-accepted（同一 accepted 集合再合并一次）。
     * 期望：幂等，仍通过。
     */
    @Test
    @Order(8)
    @DisplayName("同 accepted 再合并幂等通过")
    void merge_fixture_remergeSameAccepted_ok() throws IOException {
        runFixture("flow/merge-fixtures/remerge-same-accepted.json");
    }

    /**
     * 前提：partial-edge-only 的 patch，accepted 仅含边 8001。
     * 期望：过滤后无 addNodes，仅保留边 8001。
     */
    @Test
    @Order(9)
    @DisplayName("按 accepted 过滤仅保留勾选边")
    void filterPatchByAccepted_onlyIncludesCheckedItems() throws IOException {
        JSONObject fixture = FlowMergeFixtureTestSupport.loadFixture("flow/merge-fixtures/partial-edge-only.json");
        FlowDesignPatch patch = fixture.getObject("patch", FlowDesignPatch.class);
        Set<String> accepted = new HashSet<>(fixture.getList("acceptedIds", String.class));

        FlowDesignPatch filtered = merger.filterPatchByAccepted(patch, accepted);

        assertTrue(filtered.getAddNodes().isEmpty());
        assertEquals(1, filtered.getAddEdges().size());
        assertEquals("8001", filtered.getAddEdges().get(0).getId());
    }

    /**
     * 前提：已有节点 data.name=旧名；update 带 name/callMode。
     * 期望：浅合并后 name=新名，并写入 callMode。
     */
    @Test
    @Order(10)
    @DisplayName("applyNodeUpdate 浅合并 data")
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

    /**
     * 前提：图含 1001→1002 边，patch 删除节点 1002。
     * 期望：节点 1002 消失，边列表清空。
     */
    @Test
    @Order(12)
    @DisplayName("删除节点级联清空边")
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
