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

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link TestFlowExecutor} 集成风格单测：验证完整流程图的顺序执行与落库行为。
 * <p>
 * 被测对象按 {@link GraphWalker} 遍历图节点，调用 {@link NodeHandlerRegistry} 执行各步，
 * 通过 {@link StepResultWriter} 写入 {@link TestFlowRunStep}，最后更新 {@link TestFlowRun} 终态。
 * <p>
 * 本测试 Mock 落库服务与 HTTP/Delay Handler（桩返回 passed），Assert 走真实 {@link AssertNodeHandler}。
 * 夹具 {@code flow/linear-run-graph.json}：3×http + 1×assert 单链，共 4 个业务步 + 1 个 run_config 步。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=TestFlowExecutorTest
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
     * 线性 4 步全部通过（flow.code=0 满足 assert 规则）。
     * 期望：落库 5 条 step（含 step_index=0 的 run_config）；Run 终态 passed；
     * flow_snapshot 含 flow 变量；各步 stepDetails 含 http/flowAfter 等字段。
     */
    @Test
    @Order(1)
    void execute_linearGraph_persistsStepsAndFinishesPassed() {
        begin("execute_linearGraph_persistsStepsAndFinishesPassed");
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
        assertEquals(TestFlowExecutor.RUN_STATUS_PASSED, finished.getStatus());
        assertNotNull(finished.getFlowSnapshot());
        assertTrue(finished.getFlowSnapshot().contains("code"));

        StepResultWriter writer = new StepResultWriter();
        assertTrue(writer.parseStepDetails(persistedSteps.get(0).getStepDetails()).containsKey("scenarioLoaded"));
        String details = persistedSteps.get(1).getStepDetails();
        assertTrue(writer.parseStepDetails(details).containsKey("http"));
        assertTrue(writer.parseStepDetails(persistedSteps.get(1).getStepDetails()).containsKey("flowAfter"));

        for (TestFlowRunStep step : persistedSteps) {
            log(String.format("step[%d] node=%s type=%s status=%s",
                    step.getStepIndex(), step.getNodeId(), step.getNodeType(), step.getStatus()));
        }
        log("runStatus=" + finished.getStatus() + " executedSteps=" + outcome.getExecutedSteps());
        end("execute_linearGraph_persistsStepsAndFinishesPassed");
    }

    /**
     * assert 步失败（flow.code=99 不满足 eq 0）时执行器应短路停止。
     * 期望：仍写入失败步记录；Run 终态 failed 且含 errorCode；不再执行后续节点。
     */
    @Test
    @Order(2)
    void execute_assertFail_stopsEarly() {
        begin("execute_assertFail_stopsEarly");
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
                TestFlowExecutor.RUN_STATUS_FAILED.equals(r.getStatus())
                        && r.getErrorCode() != null
        ));

        log("passed=" + outcome.isPassed() + " errorCode=" + outcome.getErrorCode());
        log("lastStep node=" + persistedSteps.get(4).getNodeId()
                + " status=" + persistedSteps.get(4).getStatus());
        end("execute_assertFail_stopsEarly");
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
