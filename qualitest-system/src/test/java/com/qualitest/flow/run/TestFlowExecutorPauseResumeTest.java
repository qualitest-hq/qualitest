package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 TestFlowExecutor：节点失败固定 fail（合成策略不再写 prompt 暂停）。
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
    private TestFlowRun runRow;

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
            if (runRow != null && runRow.getTestFlowRunId().equals(update.getTestFlowRunId())) {
                if (update.getStatus() != null) {
                    runRow.setStatus(update.getStatus());
                }
                if (update.getRunExecutionState() != null) {
                    runRow.setRunExecutionState(update.getRunExecutionState());
                }
                if (Boolean.TRUE.equals(update.getClearExecutionState())) {
                    runRow.setRunExecutionState(null);
                }
            }
            return null;
        }).when(runPersistenceService).updateRun(any());

        AtomicInteger httpCalls = new AtomicInteger();
        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new com.qualitest.flow.node.impl.EntryNodeHandler(),
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
                new RunStatusUpdater(
                        runPersistenceService,
                        mock(com.qualitest.project.runhub.EnvRunSlotLifecycle.class)),
                new StepResultWriter(),
                new SnapshotPreExecuteHookFactory(snapshotCheckpointService, new StepResultWriter()),
                new ResumeContinuationPlanner(snapshotRestoreService),
                mock(com.qualitest.plugin.prepare.PluginRunPrepareService.class),
                mock(com.qualitest.plugin.enable.PluginEnableService.class));
    }

    /**
     * 前提：n2 http 失败；开跑合成策略固定节点 fail。
     * 期望：Run failed，不因节点失败进入 paused。
     */
    @Test
    @Order(1)
    @DisplayName("节点失败固定 fail，不暂停")
    void execute_nodeFailure_failsWithoutPause() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        // 入口 + n1 + n2；第二次 http 调用失败
        graph.setNodes(graph.getNodes().subList(0, 3));
        graph.setEdges(graph.getEdges().subList(0, 2));

        Long runId = 9001L;
        runRow = TestFlowRun.builder()
                .testFlowRunId(runId)
                .testFlowId(1L)
                .testProjectEnvId(100L)
                .runScenarioId("sc-default")
                .status(RunStatus.RUNNING.getCode())
                .graphJsonSnapshot(graph.toJsonString())
                .build();

        when(runService.selectTestFlowRunById(runId)).thenReturn(runRow);

        RunBootstrapMeta bootstrap = new RunBootstrapMeta(
                ResolvedRunScenario.builder()
                        .scenarioId("sc-default")
                        .entryNodeId(graph.getNodes().get(0).getId())
                        .testProjectEnvId(100L)
                        .build(),
                "test",
                TestProjectEnv.builder()
                        .testProjectEnvId(100L)
                        .envUrl("http://localhost:8801")
                        .allowDestructiveReset(1)
                        .build()
        );

        ExecutionOutcome first = executor.execute(
                runId, graph, FlowRunContext.builder().flow(new HashMap<>()).build(), bootstrap);

        assertFalse(first.isPaused());
        assertFalse(first.isPassed());
        assertEquals(RunStatus.FAILED.getCode(), runRow.getStatus());
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
