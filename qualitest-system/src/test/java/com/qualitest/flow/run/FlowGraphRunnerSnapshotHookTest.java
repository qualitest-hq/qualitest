package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowGraphRunner 的 preExecuteHook：插入 snapshot 步、abort、pause。
 * 边界：Stub handler + 夹具 flow/linear-run-graph.json；hook 手写返回。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerSnapshotHookTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerSnapshotHookTest {

    /**
     * 前提：首节点 snapshotBefore=true；hook 先插一条 snapshot 步。
     * 期望：首步为 snapshot，次步为 http，整体通过。
     */
    @Test
    @Order(1)
    @DisplayName("hook 在节点前插入 snapshot 步")
    void preExecuteHook_insertsSnapshotStepBeforeNode() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.getNodes().get(0).getData().put("snapshotBefore", true);

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                },
                new AssertNodeHandler()
        ));

        FlowNodePreExecuteHook hook = node -> FlowNodePreExecuteHook.PreExecuteOutcome.ok(List.of(
                StepResult.builder()
                        .nodeId(node.getId())
                        .nodeType(StepResultWriter.NODE_TYPE_SNAPSHOT)
                        .nodeName(StepResultWriter.NODE_NAME_SNAPSHOT)
                        .status(RunStatus.PASSED.getCode())
                        .durationMs(2)
                        .build()
        ));

        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build();

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry, hook);

        assertTrue(outcome.isPassed());
        assertEquals(StepResultWriter.NODE_TYPE_SNAPSHOT, outcome.getSteps().get(0).getNodeType());
        assertEquals("n1", outcome.getSteps().get(0).getNodeId());
        assertEquals("http", outcome.getSteps().get(1).getNodeType());
    }

    /**
     * 前提：hook 直接 abort（TF_SNAPSHOT_FAILED）。
     * 期望：未通过，错误码 TF_SNAPSHOT_FAILED。
     */
    @Test
    @Order(2)
    @DisplayName("hook abort 时停止图执行")
    void preExecuteHook_abortStopsGraph() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowNodePreExecuteHook hook = node -> FlowNodePreExecuteHook.PreExecuteOutcome.abort(
                StepError.of(FlowErrorCode.TF_SNAPSHOT_FAILED, "mock fail"),
                List.of()
        );

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph,
                FlowRunContext.builder().flow(new HashMap<>()).build(),
                new NodeHandlerRegistry(List.of()),
                hook
        );

        assertTrue(!outcome.isPassed());
        assertEquals("TF_SNAPSHOT_FAILED", outcome.getError().getCode());
    }

    /**
     * 前提：hook 直接 pause。
     * 期望：outcome 暂停，pauseNodeId=n1。
     */
    @Test
    @Order(3)
    @DisplayName("hook pause 时挂起图执行")
    void preExecuteHook_pauseSuspendsGraph() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowNodePreExecuteHook hook = node -> FlowNodePreExecuteHook.PreExecuteOutcome.pause(
                StepError.of(FlowErrorCode.TF_SNAPSHOT_FAILED, "mock pause"),
                List.of()
        );

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph,
                FlowRunContext.builder().flow(new HashMap<>()).build(),
                new NodeHandlerRegistry(List.of()),
                hook
        );

        assertTrue(outcome.isPaused());
        assertEquals("n1", outcome.getPauseNodeId());
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = FlowGraphRunnerSnapshotHookTest.class.getClassLoader().getResourceAsStream(path)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
