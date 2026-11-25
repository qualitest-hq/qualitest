package com.qualitest.flow.context;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.run.GraphWalker;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.project.domain.TestPluginEnvBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测入口 maxSteps / 存档点插件 onSnapshotFailure 解析，以及旧场景字段忽略。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=EntryRunPolicySupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntryRunPolicySupportTest {

    @Test
    @Order(1)
    @DisplayName("入口 data.maxSteps 读取；缺省 null")
    void maxStepsOfEntry_readsAndMissing() {
        GraphJson graph = entryGraph("http-entry", Map.of("maxSteps", 120));
        assertEquals(120, EntryRunPolicySupport.maxStepsOfEntry(graph, "e1"));
        assertNull(EntryRunPolicySupport.maxStepsOfEntry(graph, "missing"));
        assertNull(EntryRunPolicySupport.maxStepsOfEntry(
                entryGraph("scenario-entry", Map.of("doc", "")), "e1"));
    }

    @Test
    @Order(2)
    @DisplayName("协议入口同样读 data.maxSteps")
    void maxStepsOfEntry_protocolEntry() {
        GraphJson graph = entryGraph("tcp-entry", Map.of("maxSteps", 88, "doc", ""));
        assertEquals(88, EntryRunPolicySupport.maxStepsOfEntry(graph, "e1"));
        RunSnapshotPolicy policy = EntryRunPolicySupport.compose(graph, "e1", null);
        assertEquals(88, policy.getMaxSteps());
    }

    @Test
    @Order(3)
    @DisplayName("存档点绑定解析 onSnapshotFailure；未启用则 null")
    void onSnapshotFailureFromBindings() {
        TestPluginEnvBinding enabled = TestPluginEnvBinding.builder()
                .pluginId(EntryRunPolicySupport.ARCHIVE_POINT_PLUGIN_ID)
                .enabled(1)
                .delStatus(0)
                .configJson("{\"onSnapshotFailure\":\"continue\"}")
                .build();
        assertEquals("continue", EntryRunPolicySupport.onSnapshotFailureFromBindings(List.of(enabled)));

        TestPluginEnvBinding disabled = TestPluginEnvBinding.builder()
                .pluginId(EntryRunPolicySupport.ARCHIVE_POINT_PLUGIN_ID)
                .enabled(0)
                .configJson("{\"onSnapshotFailure\":\"continue\"}")
                .build();
        assertNull(EntryRunPolicySupport.onSnapshotFailureFromBindings(List.of(disabled)));
    }

    @Test
    @Order(4)
    @DisplayName("场景旧字段 onNodeFailure/onSnapshotFailure/maxSteps 读盘忽略")
    void coerceScenario_ignoresLegacyPolicyFields() {
        GraphNode entry = GraphNode.builder()
                .id("e1")
                .type("scenario-entry")
                .name("入口")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .data(new HashMap<>(Map.of(
                        "maxSteps", 200,
                        "scenarios", List.of(Map.of(
                                "id", "sc1",
                                "name", "冒烟",
                                "envId", "1",
                                "inSeed", Map.of(),
                                "onNodeFailure", "prompt",
                                "onSnapshotFailure", "continue",
                                "maxSteps", 33
                        ))
                )))
                .build();
        var scenarios = EntryScenarioSupport.scenariosOf(entry);
        assertEquals(1, scenarios.size());
        // 模型已无这些字段；toEntryScenarioMaps 也不再写出
        Map<String, Object> persisted = EntryScenarioSupport.toEntryScenarioMaps(scenarios).get(0);
        assertTrue(!persisted.containsKey("onNodeFailure"));
        assertTrue(!persisted.containsKey("onSnapshotFailure"));
        assertTrue(!persisted.containsKey("maxSteps"));
        assertEquals(GraphWalker.DEFAULT_MAX_STEPS,
                RunSnapshotPolicy.compose(
                        EntryRunPolicySupport.maxStepsOfEntry(
                                GraphJson.builder().nodes(List.of(entry)).build(), "e1"),
                        null).getMaxSteps());
    }

    private static GraphJson entryGraph(String type, Map<String, Object> data) {
        GraphNode entry = GraphNode.builder()
                .id("e1")
                .type(type)
                .name("入口")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .data(new HashMap<>(data))
                .build();
        return GraphJson.builder().nodes(List.of(entry)).build();
    }
}
