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
 * 测 RunScenarioBootstrap：从图 meta 解析运行场景与环境。
 * 覆盖：默认场景与 flowSeed、入参环境覆盖、场景不存在、fallback 回落、缺环境硬失败文案。
 * 边界：夹具 flow/linear-run-graph.json；无数据库。
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

    /**
     * 前提：场景 env 为空串，未传覆盖，但提供 fallbackEnvId。
     * 期望：使用 fallback。
     */
    @Test
    @Order(4)
    @DisplayName("解析：场景无 env 时用 fallback")
    void resolve_usesFallbackWhenScenarioEnvBlank() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.getMeta().getScenarios().get(0).setTestProjectEnvId("");
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(graph, null, null, 5555L);
        assertEquals(5555L, scenario.getTestProjectEnvId());
    }

    /**
     * 前提：场景 env 为空且无 fallback。
     * 期望：抛错且文案提示 list_project_envs。
     */
    @Test
    @Order(5)
    @DisplayName("解析：无 env 无 fallback 时文案含指引")
    void resolve_missingEnv_messageHintsListEnvs() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.getMeta().getScenarios().get(0).setTestProjectEnvId("");
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> RunScenarioBootstrap.resolve(graph, null, null, null)
        );
        assertEquals(FlowErrorCode.TF_GRAPH_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("list_project_envs"));
        assertTrue(ex.getMessage().contains("testProjectEnvId"));
        assertTrue(ex.getMessage().contains("upsert_project_env")
                || ex.getMessage().contains("submit_scenario"));
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
