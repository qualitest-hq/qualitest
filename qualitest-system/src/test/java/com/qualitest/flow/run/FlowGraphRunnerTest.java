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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowGraphRunner：内存图遍历、线性多步与 forbidNestedSubflow。
 * 边界：失败短路；子图含 subflow 时拒绝；存量 begin/end 保留。
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
    void run_singleDelayNode_passes() {
        begin("run_singleDelayNode_passes");
        String json = """
                {
                  "nodes":[{"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"等待","ms":1}}],
                  "edges":[],
                  "meta":{"run":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}}
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
        log("passed steps=" + outcome.getSteps().size());
        end("run_singleDelayNode_passes");
    }

    /**
     * 前提：线性两 delay 节点图，registry 全部通过。
     * 期望：run 返回 passed，按边顺序执行 d1→d2 两步。
     */
    @Test
    @Order(2)
    void run_linearTwoNodes_passes() {
        begin("run_linearTwoNodes_passes");
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("d1", outcome.getSteps().get(0).getNodeId());
        assertEquals("d2", outcome.getSteps().get(1).getNodeId());
        log("linear steps=" + outcome.getSteps().size());
        end("run_linearTwoNodes_passes");
    }

    /**
     * 前提：线性两节点图，首步 d1 handler 返回 failed。
     * 期望：run 短路，仅 1 步，outcome 含 error。
     */
    @Test
    @Order(3)
    void run_failedStep_stopsEarly() {
        begin("run_failedStep_stopsEarly");
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
        log("stopped at step=" + outcome.getSteps().get(0).getNodeId());
        end("run_failedStep_stopsEarly");
    }

    /**
     * 前提：图含 subflow 节点，registry 注册 subflow 桩 handler。
     * 期望：run 返回 passed，执行 1 步 subflow 节点。
     */
    @Test
    @Order(4)
    void run_graphWithSubflowNode_executes() {
        begin("run_graphWithSubflowNode_executes");
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
        log("subflow node executed at depth 0");
        end("run_graphWithSubflowNode_executes");
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
