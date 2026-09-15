package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.snapshot.SnapshotCheckpointService;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.service.ITestFlowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测 TestFlowExecutor：线性图顺序执行、落库与断言失败短路。
 * 边界：http/delay 桩 + 真实 Assert；Mock 落库与快照。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutorTest {

    private ITestFlowRunService runService;
    private RunPersistenceService runPersistenceService;
    private NodeHandlerRegistry registry;
    private TestFlowExecutor executor;
    /** 捕获 insertTestFlowRunStep 写入的行，便于断言 step_index 与 step_details */
    private List<TestFlowRunStep> persistedSteps;

    @BeforeEach
    void setUp() {
        runService = mock(ITestFlowRunService.class);
        persistedSteps = new ArrayList<>();

        // http/delay 用桩返回 passed；assert 走真实 Handler
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .nodeName(node.getId())
                                .edgeId(incomingEdgeId)
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .http(Map.of("method", "GET", "url", "/mock", "status", 200))
                                .build();
                    }
                },
                new AssertNodeHandler(),
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(0)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));

        SnapshotCheckpointService snapshotCheckpointService = mock(SnapshotCheckpointService.class);
        when(snapshotCheckpointService.maybeCheckpoint(any(), any(), any(), any(), any())).thenReturn(null);

        runPersistenceService = mock(RunPersistenceService.class);
        doAnswer(inv -> {
            TestFlowRunStep step = inv.getArgument(0);
            persistedSteps.add(step);
            return null;
        }).when(runPersistenceService).insertStep(any());
        when(runPersistenceService.casMarkRunningFromPaused(any())).thenReturn(true);
        doAnswer(inv -> {
            TestFlowRun run = inv.getArgument(0);
            runCaptorHolder.run = run;
            return null;
        }).when(runPersistenceService).updateRun(any());

        SnapshotRestoreService snapshotRestoreService = mock(SnapshotRestoreService.class);
        RunStatusUpdater runStatusUpdater = new RunStatusUpdater(runPersistenceService);
        SnapshotPreExecuteHookFactory hookFactory = new SnapshotPreExecuteHookFactory(
                snapshotCheckpointService, new StepResultWriter());
        ResumeContinuationPlanner resumePlanner = new ResumeContinuationPlanner(snapshotRestoreService);

        executor = new TestFlowExecutor(
                registry, new FlowGraphRunner(), runService, runPersistenceService,
                runStatusUpdater, new StepResultWriter(), hookFactory, resumePlanner);
    }

    private static final class RunCaptorHolder {
        TestFlowRun run;
    }

    private final RunCaptorHolder runCaptorHolder = new RunCaptorHolder();

    /**
     * 前提：linear-run-graph；flow.code=0 满足 assert。
     * 期望：passed；落库 5 步（含 run_config）；终态 passed；stepDetails 含 http/flowAfter。
     */
    @Test
    @Order(1)
    @DisplayName("线性图执行通过并落库五步")
    void execute_linearGraph_persistsStepsAndFinishesPassed() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost"))
                .flow(new HashMap<>(Map.of("code", 0)))
                .build();
        RunBootstrapMeta bootstrap = new RunBootstrapMeta(
                ResolvedRunScenario.builder()
                        .scenarioId("sc-default")
                        .scenarioName("默认场景")
                        .testProjectEnvId(100L)
                        .flowSeed(Map.of("code", 0))
                        .build(),
                "开发环境",
                null
        );

        ExecutionOutcome outcome = executor.execute(5001L, graph, ctx, bootstrap);

        assertTrue(outcome.isPassed());
        assertEquals(4, outcome.getExecutedSteps());
        assertEquals(5, persistedSteps.size());
        assertEquals(StepResultWriter.NODE_TYPE_RUN_CONFIG, persistedSteps.get(0).getNodeType());
        assertEquals(0L, persistedSteps.get(0).getStepIndex());
        assertEquals("n1", persistedSteps.get(1).getNodeId());
        assertEquals("n4", persistedSteps.get(4).getNodeId());

        ArgumentCaptor<TestFlowRun> runCaptor = ArgumentCaptor.forClass(TestFlowRun.class);
        verify(runPersistenceService).updateRun(runCaptor.capture());
        TestFlowRun finished = runCaptor.getValue();
        assertEquals(RunStatus.PASSED, finished.getStatus());
        assertNotNull(finished.getFlowSnapshot());
        assertTrue(finished.getFlowSnapshot().contains("code"));

        StepResultWriter writer = new StepResultWriter();
        assertTrue(writer.parseStepDetails(persistedSteps.get(0).getStepDetails()).containsKey("scenarioLoaded"));
        String details = persistedSteps.get(1).getStepDetails();
        assertTrue(writer.parseStepDetails(details).containsKey("http"));
        assertTrue(writer.parseStepDetails(persistedSteps.get(1).getStepDetails()).containsKey("flowAfter"));
    }

    /**
     * 前提：flow.code=99，assert 规则 eq 0 失败。
     * 期望：outcome 失败；写入失败步；Run 终态 failed 且含 errorCode。
     */
    @Test
    @Order(2)
    @DisplayName("断言失败时短路并标记 failed")
    void execute_assertFail_stopsEarly() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>(Map.of("code", 99))).build();
        RunBootstrapMeta bootstrap = new RunBootstrapMeta(
                ResolvedRunScenario.builder()
                        .scenarioId("sc-default")
                        .scenarioName("默认场景")
                        .testProjectEnvId(100L)
                        .flowSeed(Map.of("code", 99))
                        .build(),
                "开发环境",
                null
        );

        ExecutionOutcome outcome = executor.execute(5002L, graph, ctx, bootstrap);

        assertFalse(outcome.isPassed());
        assertEquals(5, persistedSteps.size());
        assertEquals(StepResult.STATUS_FAILED, persistedSteps.get(4).getStatus());

        verify(runPersistenceService).updateRun(argThat((TestFlowRun r) ->
                RunStatus.FAILED.equals(r.getStatus())
                        && r.getErrorCode() != null
        ));
    }

    /**
     * 前提：线性图；后续 HTTP 节点执行时断言前序节点步骤已入库。
     * 期望：步骤在节点完成后立刻写入，而不是整段跑完再批量补插。
     */
    @Test
    @Order(3)
    @DisplayName("节点执行中前序步骤已落库")
    void execute_persistsStepBeforeNextNodeRuns() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        if (!"n1".equals(node.getId())) {
                            assertTrue(
                                    persistedSteps.stream().anyMatch(s -> "n1".equals(s.getNodeId())),
                                    "执行 " + node.getId() + " 前 n1 应已落库");
                        }
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("http")
                                .nodeName(node.getId())
                                .edgeId(incomingEdgeId)
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .http(Map.of("method", "GET", "url", "/mock", "status", 200))
                                .build();
                    }
                },
                new AssertNodeHandler(),
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(0)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        SnapshotCheckpointService snapshotCheckpointService = mock(SnapshotCheckpointService.class);
        when(snapshotCheckpointService.maybeCheckpoint(any(), any(), any(), any(), any())).thenReturn(null);
        SnapshotRestoreService snapshotRestoreService = mock(SnapshotRestoreService.class);
        RunStatusUpdater runStatusUpdater = new RunStatusUpdater(runPersistenceService);
        SnapshotPreExecuteHookFactory hookFactory = new SnapshotPreExecuteHookFactory(
                snapshotCheckpointService, new StepResultWriter());
        ResumeContinuationPlanner resumePlanner = new ResumeContinuationPlanner(snapshotRestoreService);
        executor = new TestFlowExecutor(
                registry, new FlowGraphRunner(), runService, runPersistenceService,
                runStatusUpdater, new StepResultWriter(), hookFactory, resumePlanner);

        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost"))
                .flow(new HashMap<>(Map.of("code", 0)))
                .build();
        RunBootstrapMeta bootstrap = new RunBootstrapMeta(
                ResolvedRunScenario.builder()
                        .scenarioId("sc-default")
                        .scenarioName("默认场景")
                        .testProjectEnvId(100L)
                        .flowSeed(Map.of("code", 0))
                        .build(),
                "开发环境",
                null
        );

        ExecutionOutcome outcome = executor.execute(5003L, graph, ctx, bootstrap);
        assertTrue(outcome.isPassed());
        assertTrue(persistedSteps.stream().anyMatch(s -> "n1".equals(s.getNodeId())));
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = TestFlowExecutorTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
