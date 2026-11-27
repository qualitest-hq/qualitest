package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.model.FlowNodeType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 场景入口：仅从 entry scenarios + inSeed 开跑解析。
 */
class EntryScenarioBootstrapTest {

    @Test
    void resolvePrefersEntryInSeed() {
        GraphRunScenario sc = GraphRunScenario.builder()
                .id("sc1")
                .name("冒烟")
                .envId("100")
                .inSeed(Map.of("orderId", "O-1"))
                .build();
        GraphNode entry = new GraphNode();
        entry.setId("e1");
        entry.setType(FlowNodeType.SCENARIO_ENTRY.getCode());
        entry.setPosition(GraphNodePosition.builder().x(0).y(0).build());
        Map<String, Object> data = new HashMap<>();
        data.put("scenarios", List.of(sc));
        entry.setData(data);

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(entry))
                .meta(GraphMeta.builder().build())
                .build();

        ResolvedRunScenario resolved = RunScenarioBootstrap.resolve(graph, "sc1", null, null, null, "e1");
        assertEquals("sc1", resolved.getScenarioId());
        assertEquals("e1", resolved.getEntryNodeId());
        assertEquals(100L, resolved.getTestProjectEnvId());
        assertEquals("O-1", resolved.getInSeed().get("orderId"));
    }

    @Test
    void resolveFailsWhenEntryMissingScenarios() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of())
                .meta(GraphMeta.builder().build())
                .build();
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> RunScenarioBootstrap.resolve(graph, null, null));
        assertEquals(FlowErrorCode.TF_GRAPH_INVALID.getCode(), ex.getCode());
    }
}