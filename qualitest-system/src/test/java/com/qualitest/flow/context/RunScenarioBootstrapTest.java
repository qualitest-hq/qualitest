package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 RunScenarioBootstrap：运行前从 graph meta 解析场景（含 env 覆盖与缺失场景）。
 * 边界：夹具 flow/linear-run-graph.json；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunScenarioBootstrapTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunScenarioBootstrapTest {

    /**
     * 前提：未传 scenarioId，图 meta.activeScenarioId=sc-default。
     * 期望：scenarioId=sc-default；flowSeed.loginUser=admin；envId=9001。
     */
    @Test
    @Order(1)
    @DisplayName("解析：使用 activeScenario 与 flowSeed")
    void resolve_usesActiveScenarioAndFlowSeed() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(graph, null, null);

        assertEquals("sc-default", scenario.getScenarioId());
        assertEquals("默认", scenario.getScenarioName());
        assertEquals(9001L, scenario.getTestProjectEnvId());
        assertEquals("admin", scenario.getFlowSeed().get("loginUser"));
    }

    /**
     * 前提：API 传入 testProjectEnvId=7777，覆盖场景内 9001。
     * 期望：返回 envId=7777。
     */
    @Test
    @Order(2)
    @DisplayName("解析：API envId 覆盖场景内 env")
    void resolve_envIdOverride() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(graph, null, 7777L);
        assertEquals(7777L, scenario.getTestProjectEnvId());
    }

    /**
     * 前提：scenarioId=missing 不存在。
     * 期望：抛 FlowExecutionException，码 TF_GRAPH_INVALID。
     */
    @Test
    @Order(3)
    @DisplayName("解析：场景不存在时抛 TF_GRAPH_INVALID")
    void resolve_missingScenario_throws() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> RunScenarioBootstrap.resolve(graph, "missing", null)
        );
        assertEquals(FlowErrorCode.TF_GRAPH_INVALID.getCode(), ex.getCode());
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = RunScenarioBootstrapTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
