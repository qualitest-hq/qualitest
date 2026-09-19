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
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowGraphRunner 续跑：从中部节点继续、跳过节点、节点失败 pause。
 * 边界：Stub handler + 夹具 flow/linear-run-graph.json；无真实 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerResumeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerResumeTest {

    /**
     * 前提：continuation 从 n3 开始，HTTP stub 通过。
     * 期望：通过；步数为 2；首步为 n3；handler 只调一次。
     */
    @Test
    @Order(1)
    @DisplayName("从中部节点续跑继续执行")
    void resumeFromMiddleNode_continuesExecution() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        AtomicInteger calls = new AtomicInteger();

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        calls.incrementAndGet();
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(Map.of("code", 0)))
                                .build();
                    }
                },
                new AssertNodeHandler()
        ));

        RunContinuation continuation = RunContinuation.builder()
                .startNodeId("n3")
                .resumeMode(RunContinuation.ResumeMode.NORMAL)
                .build();

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph,
                FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build(),
                registry,
                FlowNodePreExecuteHook.NONE,
                continuation,
                RunSnapshotPolicy.defaults()
        );

        assertTrue(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("n3", outcome.getSteps().get(0).getNodeId());
        assertEquals(1, calls.get());
    }

    /**
     * 前提：resumeMode=SKIP_NODE，从 n1 续跑。
     * 期望：首步 skipped，下一步为 n2，整体通过。
     */
    @Test
    @Order(2)
    @DisplayName("SKIP_NODE 跳过节点后继续")
    void skipNode_skipsHandlerAndContinues() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(Map.of("code", 0)))
                                .build();
                    }
                },
                new AssertNodeHandler()
        ));

        RunContinuation continuation = RunContinuation.builder()
                .startNodeId("n1")
                .resumeMode(RunContinuation.ResumeMode.SKIP_NODE)
                .build();

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph,
                FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build(),
                registry,
                FlowNodePreExecuteHook.NONE,
                continuation,
                RunSnapshotPolicy.defaults()
        );

        assertTrue(outcome.isPassed());
        assertEquals(RunStatus.SKIPPED.getCode(), outcome.getSteps().get(0).getStatus());
        assertEquals("n2", outcome.getSteps().get(1).getNodeId());
    }

    /**
     * 前提：HTTP stub 失败，策略 onNodeFailure=prompt。
     * 期望：outcome 暂停，pauseNodeId=n1。
     */
    @Test
    @Order(3)
    @DisplayName("节点失败且 prompt 时暂停")
    void nodeFailureWithPrompt_pausesRun() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(RunStatus.FAILED.getCode())
                                .error(StepError.of(FlowErrorCode.TF_STEP_ERROR, "mock fail"))
                                .durationMs(1)
                                .build();
                    }
                }
        ));

        RunSnapshotPolicy policy = RunSnapshotPolicy.builder()
                .onNodeFailure(RunSnapshotPolicy.ON_NODE_FAILURE_PROMPT)
                .build();

        FlowGraphRunner runner = new FlowGraphRunner();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph,
                FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build(),
                registry,
                FlowNodePreExecuteHook.NONE,
                RunContinuation.fresh(),
                policy
        );

        assertTrue(outcome.isPaused());
        assertEquals("n1", outcome.getPauseNodeId());
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = FlowGraphRunnerResumeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing graph: " + path);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
