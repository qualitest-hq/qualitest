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
import com.qualitest.flow.model.FlowNodeType;
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
 * 测 FlowGraphRunner 的 preExecuteHook：插入 snapshot 步、abort。
 * 边界：Stub handler + 夹具 flow/linear-run-graph.json；hook 手写返回。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerSnapshotHookTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerSnapshotHookTest {

    /**
     * 前提：n1 snapshotBefore=true；hook 在业务节点前插 snapshot 步。
     * 期望：入口步后首业务 prelude 为 snapshot，再 http，整体通过。
     */
    @Test
    @Order(1)
    @DisplayName("hook 在节点前插入 snapshot 步")
    void preExecuteHook_insertsSnapshotStepBeforeNode() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        GraphNode n1 = graph.getNodes().stream()
                .filter(n -> "n1".equals(n.getId()))
                .findFirst()
                .orElseThrow();
        n1.getData().put("snapshotBefore", true);

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new com.qualitest.flow.node.impl.EntryNodeHandler(),
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

        FlowNodePreExecuteHook hook = node -> {
            if (!"n1".equals(node.getId())) {
                return FlowNodePreExecuteHook.PreExecuteOutcome.skip();
            }
            return FlowNodePreExecuteHook.PreExecuteOutcome.ok(List.of(
                    StepResult.builder()
                            .nodeId(node.getId())
                            .nodeType(StepResultWriter.NODE_TYPE_SNAPSHOT)
                            .nodeName(StepResultWriter.NODE_NAME_SNAPSHOT)
                            .status(RunStatus.PASSED.getCode())
                            .durationMs(2)
                            .build()
            ));
        };

        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build();

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry, hook);

        assertTrue(outcome.isPassed());
        StepResult snap = outcome.getSteps().stream()
                .filter(s -> StepResultWriter.NODE_TYPE_SNAPSHOT.equals(s.getNodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals("n1", snap.getNodeId());
        int snapIdx = outcome.getSteps().indexOf(snap);
        assertEquals("http", outcome.getSteps().get(snapIdx + 1).getNodeType());
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

    private static GraphJson loadGraph(String path) {
        try (InputStream in = FlowGraphRunnerSnapshotHookTest.class.getClassLoader().getResourceAsStream(path)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
