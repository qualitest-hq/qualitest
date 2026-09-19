package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.snapshot.SnapshotCheckpointService;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestFlowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 TestFlowExecutor 暂停与原地重试：onNodeFailure=prompt 时失败暂停，retry_in_place 后通过。
 * 边界：Mock 落库/快照服务；夹具 flow/linear-run-graph.json（截断为两节点）。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutorPauseResumeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutorPauseResumeTest {

    private ITestFlowRunService runService;
    private RunPersistenceService runPersistenceService;
    private SnapshotCheckpointService snapshotCheckpointService;
    private SnapshotRestoreService snapshotRestoreService;
    private TestFlowExecutor executor;
    private List<TestFlowRunStep> persistedSteps;
    private TestFlowRun pausedRun;

    @BeforeEach
    void setUp() {
        runService = mock(ITestFlowRunService.class);
        runPersistenceService = mock(RunPersistenceService.class);
        snapshotCheckpointService = mock(SnapshotCheckpointService.class);
        snapshotRestoreService = mock(SnapshotRestoreService.class);
        persistedSteps = new ArrayList<>();

        when(snapshotCheckpointService.maybeCheckpoint(any(), any(), any(), any(), any())).thenReturn(null);

        doAnswer(inv -> {
            persistedSteps.add(inv.getArgument(0));
            return null;
        }).when(runPersistenceService).insertStep(any());

        when(runPersistenceService.casMarkRunningFromPaused(any())).thenReturn(true);

        doAnswer(inv -> {
            TestFlowRun update = inv.getArgument(0);
            if (pausedRun != null && pausedRun.getTestFlowRunId().equals(update.getTestFlowRunId())) {
                if (update.getStatus() != null) {
                    pausedRun.setStatus(update.getStatus());
                }
                if (update.getRunExecutionState() != null) {
                    pausedRun.setRunExecutionState(update.getRunExecutionState());
                }
                if (Boolean.TRUE.equals(update.getClearExecutionState())) {
                    pausedRun.setRunExecutionState(null);
                }
            }
            return null;
        }).when(runPersistenceService).updateRun(any());

        AtomicInteger httpCalls = new AtomicInteger();
        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        int n = httpCalls.incrementAndGet();
                        if (n == 2) {
                            return StepResult.builder()
                                    .nodeId(node.getId())
                                    .nodeType("http")
                                    .status(RunStatus.FAILED.getCode())
                                    .error(StepError.of(com.qualitest.flow.exception.FlowErrorCode.TF_STEP_ERROR, "fail"))
                                    .durationMs(1)
                                    .build();
                        }
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));

        executor = new TestFlowExecutor(
                registry, new FlowGraphRunner(), runService, runPersistenceService,
                new RunStatusUpdater(runPersistenceService),
                new StepResultWriter(),
                new SnapshotPreExecuteHookFactory(snapshotCheckpointService, new StepResultWriter()),
                new ResumeContinuationPlanner(snapshotRestoreService));
    }

    /**
     * 前提：onNodeFailure=prompt；n2 首次 http 失败，续跑 decision=retry_in_place。
     * 期望：首次 paused 且 state 含 n2；续跑后 passed。
     */
    @Test
    @Order(1)
    @DisplayName("失败暂停后原地重试可通过")
    void execute_promptOnFailure_pausesThenRetryInPlacePasses() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.setNodes(graph.getNodes().subList(0, 2));
        graph.setEdges(graph.getEdges().subList(0, 1));

        Long runId = 9001L;
        pausedRun = TestFlowRun.builder()
                .testFlowRunId(runId)
                .testFlowId(1L)
                .testProjectEnvId(100L)
                .runScenarioId("sc-default")
                .status(RunStatus.RUNNING.getCode())
                .graphJsonSnapshot(graph.toJsonString())
                .build();

        when(runService.selectTestFlowRunById(runId)).thenReturn(pausedRun);

        RunBootstrapMeta bootstrap = new RunBootstrapMeta(
                ResolvedRunScenario.builder()
                        .scenarioId("sc-default")
                        .testProjectEnvId(100L)
                        .onNodeFailure(RunSnapshotPolicy.ON_NODE_FAILURE_PROMPT)
                        .build(),
                "test",
                TestProjectEnv.builder().envUrl("http://localhost:8801").allowDestructiveReset(1).build()
        );

        ExecutionOutcome first = executor.execute(
                runId, graph, FlowRunContext.builder().flow(new HashMap<>()).build(), bootstrap);

        assertTrue(first.isPaused());
        assertEquals(RunStatus.PAUSED.getCode(), pausedRun.getStatus());
        assertTrue(pausedRun.getRunExecutionState() != null && pausedRun.getRunExecutionState().contains("n2"));

        pausedRun.setStatus(RunStatus.PAUSED.getCode());
        ExecutionOutcome resumed = executor.resume(
                runId,
                ResumeDecision.builder().decision(ResumeDecision.RETRY_IN_PLACE).operator("tester").build(),
                TestProjectEnv.builder().envUrl("http://localhost:8801").allowDestructiveReset(1).build()
        );

        assertTrue(resumed.isPassed());
        assertEquals(RunStatus.PASSED.getCode(), pausedRun.getStatus());
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = TestFlowExecutorPauseResumeTest.class.getClassLoader().getResourceAsStream(path)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
