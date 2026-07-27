package com.qualitest.flow.model;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphJson：demo-graph.json 反序列化、场景字段、condition 分支 target、serialize 往返。
 * 边界：仅 classpath 夹具；存量 begin/end 保留。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphJsonTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphJsonTest {

    /** fixture 默认场景（雪花 id） */
    private static final String SCENARIO_DEFAULT = "2042000000000000101";
    /** fixture 预发轮询场景 */
    private static final String SCENARIO_STAGING_POLL = "2042000000000000103";

    /**
     * fixture 原始 JSON 文本
     */
    private String fixtureJson;

    /**
     * 由 fixture 解析得到的图模型，供各用例共用
     */
    private GraphJson demoGraph;

    /**
     * 每个 @Test 执行前从 classpath 加载 demo-graph.json。
     */
    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/flow/demo-graph.json")) {
            assertNotNull(in, "fixture /flow/demo-graph.json");
            fixtureJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        demoGraph = GraphJson.parse(fixtureJson);
    }

    /**
     * 前提：加载 demo-graph.json。
     * 期望：11 节点、11 边、3 场景，meta.scenarios 非空。
     */
    @Test
    @Order(1)
    void demoGraph_deserializesFromFixture() {
        begin("demoGraph_deserializesFromFixture");
        assertNotNull(demoGraph);
        assertNotNull(demoGraph.getNodes());
        assertNotNull(demoGraph.getEdges());
        assertNotNull(demoGraph.getMeta());
        assertEquals(11, demoGraph.getNodes().size());
        assertEquals(11, demoGraph.getEdges().size());
        assertNotNull(demoGraph.getMeta().getScenarios());
        assertEquals(3, demoGraph.getMeta().getScenarios().size());
        log("nodes=11, edges=11, scenarios=3");
        end("demoGraph_deserializesFromFixture");
    }

    /**
     * 前提：已解析的 demo 图。
     * 期望：各场景 id/name/env/flowSeed/remark 齐全；预发场景 bizId=rpt-99。
     */
    @Test
    @Order(2)
    void demoGraph_metaRunFieldsPresent() {
        begin("demoGraph_metaRunFieldsPresent");
        GraphMeta meta = demoGraph.getMeta();
        assertEquals(SCENARIO_DEFAULT, meta.getActiveScenarioId());
        log("activeScenarioId=" + meta.getActiveScenarioId());

        for (GraphRunScenario scenario : meta.getScenarios()) {
            assertNotNull(scenario.getId(), "scenario.id");
            assertTrue(scenario.getId().matches("\\d+"), "scenario.id 应为雪花数字串: " + scenario.getId());
            assertNotNull(scenario.getName(), "scenario.name");
            assertNotNull(scenario.getTestProjectEnvId(), "scenario.testProjectEnvId");
            assertNotNull(scenario.getFlowSeed(), "scenario.flowSeed");
            assertFalse(scenario.getFlowSeed().isEmpty(), "scenario.flowSeed 非空");
            assertNotNull(scenario.getRemark(), "scenario.remark");
            log("scenario " + scenario.getId() + " -> envId=" + scenario.getTestProjectEnvId());
        }

        GraphRunScenario staging = meta.getScenarios().stream()
                .filter(s -> SCENARIO_STAGING_POLL.equals(s.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("rpt-99", staging.getFlowSeed().get("bizId"));
        assertEquals("2042000000000000002", staging.getTestProjectEnvId());
        log("staging.flowSeed.bizId=rpt-99");
        end("demoGraph_metaRunFieldsPresent");
    }

    /**
     * 前提：demo 含 2 个 condition 节点。
     * 期望：每个 branch 含 id 与 target。
     */
    @Test
    @Order(3)
    void demoGraph_conditionBranchesHaveTarget() {
        begin("demoGraph_conditionBranchesHaveTarget");
        long conditionCount = demoGraph.getNodes().stream()
                .filter(n -> "condition".equals(n.getType()))
                .peek(n -> {
                    @SuppressWarnings("unchecked")
                    var branches = (java.util.List<Map<String, Object>>) n.getData().get("branches");
                    assertNotNull(branches, "branches");
                    assertFalse(branches.isEmpty());
                    for (Map<String, Object> branch : branches) {
                        assertNotNull(branch.get("id"), "branch.id");
                        assertNotNull(branch.get("target"), "branch.target");
                        log("condition " + n.getId() + " branch " + branch.get("id") + " -> " + branch.get("target"));
                    }
                })
                .count();
        assertEquals(2, conditionCount);
        end("demoGraph_conditionBranchesHaveTarget");
    }

    /**
     * 前提：parse → toJsonString → parse。
     * 期望：scenarios、activeScenarioId、节点 id、边 source/target 均保持。
     */
    @Test
    @Order(4)
    void serializeRoundTrip_preservesGraph() {
        begin("serializeRoundTrip_preservesGraph");
        GraphJson roundTripped = GraphJson.parse(demoGraph.toJsonString());

        assertEquals(
                JSON.toJSONString(demoGraph.getMeta().getScenarios()),
                JSON.toJSONString(roundTripped.getMeta().getScenarios())
        );
        assertEquals(SCENARIO_DEFAULT, roundTripped.getMeta().getActiveScenarioId());
        log("scenarios preserved, activeScenarioId=" + SCENARIO_DEFAULT);

        Set<String> originalNodeIds = demoGraph.getNodes().stream()
                .map(GraphNode::getId)
                .collect(Collectors.toSet());
        Set<String> roundTripNodeIds = roundTripped.getNodes().stream()
                .map(GraphNode::getId)
                .collect(Collectors.toSet());
        assertEquals(originalNodeIds, roundTripNodeIds);
        log("node ids preserved: " + originalNodeIds.size());

        for (int i = 0; i < demoGraph.getEdges().size(); i++) {
            GraphEdge original = demoGraph.getEdges().get(i);
            GraphEdge copied = roundTripped.getEdges().get(i);
            assertEquals(original.getId(), copied.getId());
            assertEquals(original.getSource(), copied.getSource());
            assertEquals(original.getTarget(), copied.getTarget());
        }
        log("edges preserved: " + demoGraph.getEdges().size());
        end("serializeRoundTrip_preservesGraph");
    }
}
