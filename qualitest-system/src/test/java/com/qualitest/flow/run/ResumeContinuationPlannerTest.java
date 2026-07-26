package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ResumeContinuationPlanner} 单元测试：resume 决策到续跑计划的翻译。
 * <p>
 * 夹具 {@code flow/linear-run-graph.json}。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ResumeContinuationPlannerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResumeContinuationPlannerTest {

    private final SnapshotRestoreService restoreService = mock(SnapshotRestoreService.class);
    private final ResumeContinuationPlanner planner = new ResumeContinuationPlanner(restoreService);

    /**
     * restoreAndRetry 指定 snap-1。
     * 期望：有 restore 步；continuation 从 n1 重试；栈截断后仅保留 snap-1。
     */
    @Test
    @Order(1)
    void plan_restoreAndRetry_truncatesStack() {
        begin("plan_restoreAndRetry_truncatesStack");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunSnapshotState stack = new FlowRunSnapshotState();
        stack.push("n1", "snap-1");
        stack.push("n2", "snap-2");

        when(restoreService.restore(any(), eq(99L), eq("snap-1"), eq("n1")))
                .thenReturn(StepResult.builder().nodeType(StepResultWriter.NODE_TYPE_RESTORE).status(StepResult.STATUS_PASSED).build());

        RunExecutionState state = RunExecutionState.builder().pauseNodeId("n2").build();
        ResumeContinuationPlanner.PlannedResume planned = planner.plan(
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).snapshotId("snap-1").build(),
                state, graph, stack, TestProjectEnv.builder().allowDestructiveReset(1).envUrl("http://localhost:8081").build(), 99L);

        assertNotNull(planned.getRestoreStep());
        assertEquals("n1", planned.getContinuation().getStartNodeId());
        assertEquals(1, stack.entries().size());
        assertEquals("snap-1", stack.peek().getSnapshotId());
        log("startNodeId=n1 stackSize=1 restoreStep=present");
        end("plan_restoreAndRetry_truncatesStack");
    }

    /**
     * 环境未允许还原时的 restoreAndRetry。
     * 期望：restoreStep=null；仍从 n1 RETRY_NODE 续跑。
     */
    @Test
    @Order(2)
    void plan_restoreAndRetry_silentSkipRestoreWhenEnvDeniesReset() {
        begin("plan_restoreAndRetry_silentSkipRestoreWhenEnvDeniesReset");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunSnapshotState stack = new FlowRunSnapshotState();
        stack.push("n1", "snap-1");

        SnapshotRestoreService realRestore = new SnapshotRestoreService(
                mock(com.qualitest.flow.snapshot.DbSnapshotAdapter.class), new StepResultWriter());
        ResumeContinuationPlanner localPlanner = new ResumeContinuationPlanner(realRestore);

        RunExecutionState state = RunExecutionState.builder().pauseNodeId("n2").build();
        ResumeContinuationPlanner.PlannedResume planned = localPlanner.plan(
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).snapshotId("snap-1").build(),
                state, graph, stack,
                TestProjectEnv.builder().allowDestructiveReset(0).envUrl("http://localhost:8081").build(), 99L);

        assertNull(planned.getRestoreStep());
        assertEquals("n1", planned.getContinuation().getStartNodeId());
        assertEquals(RunContinuation.ResumeMode.RETRY_NODE, planned.getContinuation().getResumeMode());
        log("restoreStep=null startNodeId=n1 mode=RETRY_NODE");
        end("plan_restoreAndRetry_silentSkipRestoreWhenEnvDeniesReset");
    }

    /**
     * skip 决策。
     * 期望：无 restore 步；resumeMode=SKIP_NODE。
     */
    @Test
    @Order(3)
    void plan_skip_usesSkipMode() {
        begin("plan_skip_usesSkipMode");
        RunExecutionState state = RunExecutionState.builder().pauseNodeId("n2").incomingEdgeId("e1").build();
        ResumeContinuationPlanner.PlannedResume planned = planner.plan(
                ResumeDecision.builder().decision(ResumeDecision.SKIP).build(),
                state, loadGraph("flow/linear-run-graph.json"), new FlowRunSnapshotState(),
                TestProjectEnv.builder().build(), 1L);

        assertNull(planned.getRestoreStep());
        assertEquals(RunContinuation.ResumeMode.SKIP_NODE, planned.getContinuation().getResumeMode());
        log("mode=SKIP_NODE");
        end("plan_skip_usesSkipMode");
    }

    /**
     * 未传 snapshotId，暂停节点为 n2 且栈中有 n1、n2 两条记录。
     * 期望：回退到 n2 对应的最后一次 checkpoint snap-b。
     */
    @Test
    @Order(4)
    void resolveSnapshotId_fallsBackToPauseNode() {
        begin("resolveSnapshotId_fallsBackToPauseNode");
        FlowRunSnapshotState stack = new FlowRunSnapshotState();
        stack.push("n1", "snap-a");
        stack.push("n2", "snap-b");

        String id = ResumeContinuationPlanner.resolveSnapshotId(
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).build(),
                stack, "n2");
        assertEquals("snap-b", id);
        log("resolvedSnapshotId=snap-b");
        end("resolveSnapshotId_fallsBackToPauseNode");
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = ResumeContinuationPlannerTest.class.getClassLoader().getResourceAsStream(path)) {
            return GraphJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
