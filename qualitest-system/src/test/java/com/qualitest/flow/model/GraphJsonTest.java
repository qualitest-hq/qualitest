package com.qualitest.flow.model;

import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphJson：demo-graph.json 反序列化、场景字段、condition 分支 target、serialize 往返。
 * 边界：仅 classpath 夹具；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphJsonTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphJsonTest {

    private static final String SCENARIO_DEFAULT = "2042000000000000101";
    private static final String SCENARIO_STAGING_POLL = "2042000000000000103";

    private String fixtureJson;
    private GraphJson demoGraph;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/flow/demo-graph.json")) {
            assertNotNull(in, "fixture /flow/demo-graph.json");
            fixtureJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        demoGraph = GraphJson.parse(fixtureJson);
    }

    @Test
    @Order(1)
    @DisplayName("demo-graph 反序列化节点边场景")
    void demoGraph_deserializesFromFixture() {
        assertNotNull(demoGraph);
        assertNotNull(demoGraph.getNodes());
        assertNotNull(demoGraph.getEdges());
        assertNotNull(demoGraph.getMeta());
        assertEquals(12, demoGraph.getNodes().size());
        assertEquals(12, demoGraph.getEdges().size());
        List<GraphRunScenario> scenarios = EntryScenarioSupport.allEntryScenarios(demoGraph);
        assertEquals(3, scenarios.size());
    }

    @Test
    @Order(2)
    @DisplayName("场景字段齐全且预发 bizId 正确")
    void demoGraph_entryRunFieldsPresent() {
        List<GraphRunScenario> scenarios = EntryScenarioSupport.allEntryScenarios(demoGraph);
        assertEquals(SCENARIO_DEFAULT, scenarios.get(0).getId());

        for (GraphRunScenario scenario : scenarios) {
            assertNotNull(scenario.getId(), "scenario.id");
            assertTrue(scenario.getId().matches("\\d+"), "scenario.id 应为雪花数字串: " + scenario.getId());
            assertNotNull(scenario.getName(), "scenario.name");
            assertNotNull(scenario.getEnvId(), "scenario.envId");
            Map<String, Object> inSeed = scenario.getInSeed();
            assertNotNull(inSeed, "scenario.inSeed");
            assertFalse(inSeed.isEmpty(), "scenario.inSeed 非空");
            assertNotNull(scenario.getRemark(), "scenario.remark");
        }

        GraphRunScenario staging = scenarios.stream()
                .filter(s -> SCENARIO_STAGING_POLL.equals(s.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("rpt-99", staging.getInSeed().get("bizId"));
        assertEquals("2042000000000000002", staging.getEnvId());
    }

    @Test
    @Order(3)
    @DisplayName("condition 分支含 id 与 target")
    void demoGraph_conditionBranchesHaveTarget() {
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
                    }
                })
                .count();
        assertEquals(2, conditionCount);
    }

    @Test
    @Order(4)
    @DisplayName("serialize 往返保持图结构")
    void serializeRoundTrip_preservesGraph() {
        GraphJson roundTripped = GraphJson.parse(demoGraph.toJsonString());

        assertEquals(
                EntryScenarioSupport.allEntryScenarios(demoGraph).size(),
                EntryScenarioSupport.allEntryScenarios(roundTripped).size());

        Set<String> originalNodeIds = demoGraph.getNodes().stream()
                .map(GraphNode::getId)
                .collect(Collectors.toSet());
        Set<String> roundTripNodeIds = roundTripped.getNodes().stream()
                .map(GraphNode::getId)
                .collect(Collectors.toSet());
        assertEquals(originalNodeIds, roundTripNodeIds);

        for (int i = 0; i < demoGraph.getEdges().size(); i++) {
            GraphEdge original = demoGraph.getEdges().get(i);
            GraphEdge roundTrip = roundTripped.getEdges().get(i);
            assertEquals(original.getSource(), roundTrip.getSource());
            assertEquals(original.getTarget(), roundTrip.getTarget());
        }

        long entryCount = roundTripped.getNodes().stream()
                .filter(n -> FlowNodeType.SCENARIO_ENTRY.matches(n.getType()))
                .count();
        assertEquals(1, entryCount);
    }
}