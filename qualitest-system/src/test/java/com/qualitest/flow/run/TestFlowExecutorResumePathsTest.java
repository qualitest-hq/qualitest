package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextPersistence;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.snapshot.SnapshotCheckpointService;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.flow.snapshot.SnapshotStackEntry;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestFlowRun;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 TestFlowExecutor.resume：四种决策 + 幂等 + 环境静默跳过还原。
 * 边界：Mock 落库与 SnapshotCheckpointService；夹具 flow/linear-run-graph.json。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutorResumePathsTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutorResumePathsTest {

    private ITestFlowRunService runService;
    private RunPersistenceService runPersistenceService;
    private SnapshotRestoreService snapshotRestoreService;
    private TestFlowExecutor executor;
    private TestFlowRun pausedRun;

    @BeforeEach
    void setUp() {
        runService = mock(ITestFlowRunService.class);
        runPersistenceService = mock(RunPersistenceService.class);
        snapshotRestoreService = mock(SnapshotRestoreService.class);

        doAnswer(inv -> null).when(runPersistenceService).insertStep(any());
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

        SnapshotCheckpointService snapshotCheckpointService = mock(SnapshotCheckpointService.class);
        when(snapshotCheckpointService.maybeCheckpoint(any(), any(), any(), any(), any())).thenReturn(null);

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                },
                new AssertNodeHandler()
        ));

        executor = new TestFlowExecutor(
                registry, new FlowGraphRunner(), runService, runPersistenceService,
                new RunStatusUpdater(runPersistenceService),
                new StepResultWriter(),
                new SnapshotPreExecuteHookFactory(snapshotCheckpointService, new StepResultWriter()),
                new ResumeContinuationPlanner(snapshotRestoreService));
    }

    /**
     * 前提：Run 非 paused，再次 resume。
     * 期望：幂等返回 TF_RUN_NOT_PAUSED。
     */
    @Test
    @Order(1)
    @DisplayName("非暂停态续跑幂等返回 TF_RUN_NOT_PAUSED")
    void resume_idempotentWhenNotPaused() {
        pausedRun = TestFlowRun.builder().testFlowRunId(1L).status(RunStatus.PASSED).build();
        when(runService.selectTestFlowRunById(1L)).thenReturn(pausedRun);

        ExecutionOutcome outcome = executor.resume(1L,
                ResumeDecision.builder().decision(ResumeDecision.RETRY_IN_PLACE).build(),
                TestProjectEnv.builder().build());

        assertTrue(outcome.isIdempotent());
        assertEquals(FlowErrorCode.TF_RUN_NOT_PAUSED.getCode(), outcome.getErrorCode());
    }

    /**
     * 前提：paused Run；decision=abort。
     * 期望：终态 aborted。
     */
    @Test
    @Order(2)
    @DisplayName("abort 决策将运行标记为 aborted")
    void resume_abort_marksAborted() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        pausedRun = buildPausedRun(2L, graph, "n4");
        when(runService.selectTestFlowRunById(2L)).thenReturn(pausedRun);

        ExecutionOutcome outcome = executor.resume(2L,
                ResumeDecision.builder().decision(ResumeDecision.ABORT).operator("u").build(),
                env());

        assertTrue(outcome.isAborted());
        assertEquals(RunStatus.ABORTED, pausedRun.getStatus());
    }

    /**
     * 前提：paused 在中间节点；decision=skip。
     * 期望：跳过暂停节点并跑完剩余图，终态 passed。
     */
    @Test
    @Order(3)
    @DisplayName("skip 决策跳过暂停节点并跑完")
    void resume_skip_completesRun() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        pausedRun = buildPausedRun(3L, graph, "n4");
        when(runService.selectTestFlowRunById(3L)).thenReturn(pausedRun);

        ExecutionOutcome outcome = executor.resume(3L,
                ResumeDecision.builder().decision(ResumeDecision.SKIP).build(),
                env());

        assertTrue(outcome.isPassed());
        assertEquals(RunStatus.PASSED, pausedRun.getStatus());
    }

    /**
     * 前提：paused；restoreAndRetry；Mock restore 成功。
     * 期望：从快照节点重跑，终态 passed。
     */
    @Test
    @Order(4)
    @DisplayName("restoreAndRetry 从快照重跑通过")
    void resume_restoreAndRetry_completesRun() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.setNodes(graph.getNodes().subList(0, 2));
        graph.setEdges(graph.getEdges().subList(0, 1));

        pausedRun = buildPausedRun(4L, graph, "n2");
        RunExecutionState state = RunExecutionState.fromJson(pausedRun.getRunExecutionState());
        state.setSnapshotStack(List.of(new SnapshotStackEntry("n1", "snap-1")));
        pausedRun.setRunExecutionState(state.toJson());
        when(runService.selectTestFlowRunById(4L)).thenReturn(pausedRun);

        when(snapshotRestoreService.restore(any(), any(), any(), any()))
                .thenReturn(new StepResultWriter().toRestoreStepResult(
                        "n1", "snap-1", "http://localhost:8081/test-support", 1));

        ExecutionOutcome outcome = executor.resume(4L,
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).snapshotId("snap-1").build(),
                env());

        assertTrue(outcome.isPassed());
        assertEquals(RunStatus.PASSED, pausedRun.getStatus());
    }

    /**
     * 前提：restoreAndRetry，但 allowDestructiveReset=0。
     * 期望：跳过 restore 调用，仍从快照节点续跑通过。
     */
    @Test
    @Order(5)
    @DisplayName("禁止破坏性还原时跳过 restore 仍通过")
    void resume_restoreAndRetry_skipsRestoreWhenEnvDeniesReset() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        graph.setNodes(graph.getNodes().subList(0, 2));
        graph.setEdges(graph.getEdges().subList(0, 1));

        pausedRun = buildPausedRun(5L, graph, "n2");
        RunExecutionState state = RunExecutionState.fromJson(pausedRun.getRunExecutionState());
        state.setSnapshotStack(List.of(new SnapshotStackEntry("n1", "snap-1")));
        pausedRun.setRunExecutionState(state.toJson());
        when(runService.selectTestFlowRunById(5L)).thenReturn(pausedRun);

        SnapshotRestoreService realRestore = new SnapshotRestoreService(
                mock(com.qualitest.flow.snapshot.DbSnapshotAdapter.class), new StepResultWriter());
        executor = new TestFlowExecutor(
                new NodeHandlerRegistry(List.of(
                        new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                            @Override
                            public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                                return StepResult.builder()
                                        .nodeId(node.getId())
                                        .nodeType("http")
                                        .status(StepResult.STATUS_PASSED)
                                        .durationMs(1)
                                        .flowAfter(new HashMap<>(ctx.getFlow()))
                                        .build();
                            }
                        },
                        new AssertNodeHandler()
                )),
                new FlowGraphRunner(), runService, runPersistenceService,
                new RunStatusUpdater(runPersistenceService),
                new StepResultWriter(),
                new SnapshotPreExecuteHookFactory(mock(SnapshotCheckpointService.class), new StepResultWriter()),
                new ResumeContinuationPlanner(realRestore));

        ExecutionOutcome outcome = executor.resume(5L,
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).snapshotId("snap-1").build(),
                TestProjectEnv.builder().envUrl("http://localhost:8081").allowDestructiveReset(0).build());

        assertTrue(outcome.isPassed());
        assertEquals(RunStatus.PASSED, pausedRun.getStatus());
    }

    private static TestFlowRun buildPausedRun(Long runId, GraphJson graph, String pauseNodeId) {
        RunExecutionState state = RunExecutionState.builder()
                .nextStepIndex(5)
                .pauseNodeId(pauseNodeId)
                .pauseReason(RunExecutionState.PAUSE_REASON_NODE_FAILURE)
                .context(FlowRunContextPersistence.toMap(
                        FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 0))).build()))
                .build();
        return TestFlowRun.builder()
                .testFlowRunId(runId)
                .testProjectEnvId(100L)
                .runScenarioId("sc-default")
                .status(RunStatus.PAUSED)
                .graphJsonSnapshot(graph.toJsonString())
                .runExecutionState(state.toJson())
                .build();
    }

    private static TestProjectEnv env() {
        return TestProjectEnv.builder().envUrl("http://localhost:8081").allowDestructiveReset(1).build();
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = TestFlowExecutorResumePathsTest.class.getClassLoader().getResourceAsStream(path)) {
            return GraphJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

