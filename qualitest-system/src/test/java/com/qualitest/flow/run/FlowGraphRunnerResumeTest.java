package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGraphRunnerResumeTest {

    @Test
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
                                .status(StepResult.STATUS_PASSED)
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

    @Test
    void skipNode_skipsHandlerAndContinues() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(StepResult.STATUS_PASSED)
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
        assertEquals(StepResultWriter.STATUS_SKIPPED, outcome.getSteps().get(0).getStatus());
        assertEquals("n2", outcome.getSteps().get(1).getNodeId());
    }

    @Test
    void nodeFailureWithPrompt_pausesRun() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(StepResult.STATUS_FAILED)
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
