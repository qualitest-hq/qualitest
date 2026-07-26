package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.qualitest.flow.support.FlowTestSections.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RunScenarioBootstrap} 单元测试：验证流程运行前「场景」的解析与校验。
 * <p>
 * 被测对象从 {@link GraphJson#getMeta()} 的 run.scenarios 中查找目标场景，
 * 返回 {@link ResolvedRunScenario}（含 scenarioId、环境 id、flowSeed 初始变量）。
 * 支持 API 入参覆盖 testProjectEnvId；找不到场景时抛 {@link FlowErrorCode#TF_GRAPH_INVALID}。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=RunScenarioBootstrapTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunScenarioBootstrapTest {

    /**
     * 未显式传入 scenarioId 时，应使用 graph meta.run.activeScenarioId 对应的场景。
     * 期望：scenarioId=sc-default、flowSeed 含 loginUser=admin、testProjectEnvId=9001。
     */
    @Test
    @Order(1)
    void resolve_usesActiveScenarioAndFlowSeed() {
        begin("resolve_usesActiveScenarioAndFlowSeed");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(graph, null, null);

        assertEquals("sc-default", scenario.getScenarioId());
        assertEquals("默认", scenario.getScenarioName());
        assertEquals(9001L, scenario.getTestProjectEnvId());
        assertEquals("admin", scenario.getFlowSeed().get("loginUser"));

        log("scenarioId=" + scenario.getScenarioId() + " name=" + scenario.getScenarioName());
        log("envId=" + scenario.getTestProjectEnvId() + " flowSeed.loginUser=" + scenario.getFlowSeed().get("loginUser"));
        end("resolve_usesActiveScenarioAndFlowSeed");
    }

    /**
     * API 调用方传入的 testProjectEnvId 应优先于场景内配置的环境 id。
     * 期望：返回的 testProjectEnvId=7777（覆盖场景原值 9001）。
     */
    @Test
    @Order(2)
    void resolve_envIdOverride() {
        begin("resolve_envIdOverride");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(graph, null, 7777L);
        assertEquals(7777L, scenario.getTestProjectEnvId());
        log("envId override -> " + scenario.getTestProjectEnvId());
        end("resolve_envIdOverride");
    }

    /**
     * 指定不存在的 scenarioId 时无法启动运行。
     * 期望：抛 {@link FlowExecutionException}，错误码 {@link FlowErrorCode#TF_GRAPH_INVALID}。
     */
    @Test
    @Order(3)
    void resolve_missingScenario_throws() {
        begin("resolve_missingScenario_throws");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> RunScenarioBootstrap.resolve(graph, "missing", null)
        );
        assertEquals(FlowErrorCode.TF_GRAPH_INVALID.getCode(), ex.getCode());
        log("rejected code=" + ex.getCode() + " message=" + ex.getMessage());
        end("resolve_missingScenario_throws");
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
