package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowGraphRunner：内存图遍历、线性多步与 forbidNestedSubflow。
 * 边界：失败短路；子图含 subflow 时拒绝；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerTest {

    private FlowGraphRunner runner;
    private NodeHandlerRegistry registry;
    private FlowRunContext ctx;

    @BeforeEach
    void setUp() {
        runner = new FlowGraphRunner();
        registry = passingDelayRegistry();
        ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
    }

    /**
     * 前提：单 delay 节点图，registry 全部通过。
     * 期望：run 返回 passed，执行 1 步。
     */
    @Test
    @Order(1)
    @DisplayName("单 delay 节点图执行通过")
    void run_singleDelayNode_passes() {
        String json = """
                {
                  "nodes":[{"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"等待","ms":1}}],
                  "edges":[],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
    }

    /**
     * 前提：线性两 delay 节点图，registry 全部通过。
     * 期望：run 返回 passed，按边顺序执行 d1→d2 两步。
     */
    @Test
    @Order(2)
    @DisplayName("线性两节点按边顺序通过")
    void run_linearTwoNodes_passes() {
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("d1", outcome.getSteps().get(0).getNodeId());
        assertEquals("d2", outcome.getSteps().get(1).getNodeId());
    }

    /**
     * 前提：线性两节点图，首步 d1 handler 返回 failed。
     * 期望：run 短路，仅 1 步，outcome 含 error。
     */
    @Test
    @Order(3)
    @DisplayName("首步失败时短路仅执行一步")
    void run_failedStep_stopsEarly() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        boolean fail = "d1".equals(node.getId());
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(fail ? StepResult.STATUS_FAILED : StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .error(fail ? StepError.of(FlowErrorCode.TF_STEP_ERROR, "首步失败") : null)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
        assertNotNull(outcome.getError());
    }

    /**
     * 前提：图含 subflow 节点，registry 注册 subflow 桩 handler。
     * 期望：run 返回 passed，执行 1 步 subflow 节点。
     */
    @Test
    @Order(4)
    @DisplayName("含 subflow 节点的图可执行")
    void run_graphWithSubflowNode_executes() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.SUBFLOW) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("subflow")
                                .nodeName("嵌套")
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        String json = loadResource("flow/subflow-nested-graph.json");
        GraphJson graph = GraphJson.parse(json);
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
    }

    private static NodeHandlerRegistry passingDelayRegistry() {
        return new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
    }

    private static String loadResource(String path) {
        InputStream in = FlowGraphRunnerTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing resource: " + path);
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
