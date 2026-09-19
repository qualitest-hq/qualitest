package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 ResumeContinuationPlanner：resume 决策到续跑计划的翻译。
 * 边界：Mock SnapshotRestoreService；夹具 flow/linear-run-graph.json。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ResumeContinuationPlannerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResumeContinuationPlannerTest {

    private final SnapshotRestoreService restoreService = mock(SnapshotRestoreService.class);
    private final ResumeContinuationPlanner planner = new ResumeContinuationPlanner(restoreService);

    /**
     * 前提：restoreAndRetry 指定 snap-1，栈上已有 snap-1 / snap-2。
     * 期望：有 restore 步；continuation 从 n1 重试；栈截断后仅保留 snap-1。
     */
    @Test
    @Order(1)
    @DisplayName("restoreAndRetry 截断快照栈")
    void plan_restoreAndRetry_truncatesStack() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        FlowRunSnapshotState stack = new FlowRunSnapshotState();
        stack.push("n1", "snap-1");
        stack.push("n2", "snap-2");

        when(restoreService.restore(any(), eq(99L), eq("snap-1"), eq("n1")))
                .thenReturn(StepResult.builder().nodeType(StepResultWriter.NODE_TYPE_RESTORE).status(RunStatus.PASSED.getCode()).build());

        RunExecutionState state = RunExecutionState.builder().pauseNodeId("n2").build();
        ResumeContinuationPlanner.PlannedResume planned = planner.plan(
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).snapshotId("snap-1").build(),
                state, graph, stack, TestProjectEnv.builder().allowDestructiveReset(1).envUrl("http://localhost:8801").build(), 99L);

        assertNotNull(planned.getRestoreStep());
        assertEquals("n1", planned.getContinuation().getStartNodeId());
        assertEquals(1, stack.entries().size());
        assertEquals("snap-1", stack.peek().getSnapshotId());
    }

    /**
     * 前提：restoreAndRetry + snap-1，但 allowDestructiveReset=0。
     * 期望：restoreStep=null；仍从 n1 以 RETRY_NODE 续跑。
     */
    @Test
    @Order(2)
    @DisplayName("环境禁止重置时静默跳过 restore")
    void plan_restoreAndRetry_silentSkipRestoreWhenEnvDeniesReset() {
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
                TestProjectEnv.builder().allowDestructiveReset(0).envUrl("http://localhost:8801").build(), 99L);

        assertNull(planned.getRestoreStep());
        assertEquals("n1", planned.getContinuation().getStartNodeId());
        assertEquals(RunContinuation.ResumeMode.RETRY_NODE, planned.getContinuation().getResumeMode());
    }

    /**
     * 前提：decision=SKIP，pauseNodeId=n2。
     * 期望：无 restore 步；resumeMode=SKIP_NODE。
     */
    @Test
    @Order(3)
    @DisplayName("SKIP 决策使用 SKIP_NODE 模式")
    void plan_skip_usesSkipMode() {
        RunExecutionState state = RunExecutionState.builder().pauseNodeId("n2").incomingEdgeId("e1").build();
        ResumeContinuationPlanner.PlannedResume planned = planner.plan(
                ResumeDecision.builder().decision(ResumeDecision.SKIP).build(),
                state, loadGraph("flow/linear-run-graph.json"), new FlowRunSnapshotState(),
                TestProjectEnv.builder().build(), 1L);

        assertNull(planned.getRestoreStep());
        assertEquals(RunContinuation.ResumeMode.SKIP_NODE, planned.getContinuation().getResumeMode());
    }

    /**
     * 前提：未传 snapshotId；pause=n2；栈含 n1→snap-a、n2→snap-b。
     * 期望：resolveSnapshotId 回退为 snap-b。
     */
    @Test
    @Order(4)
    @DisplayName("未传 snapshotId 时回退到暂停节点")
    void resolveSnapshotId_fallsBackToPauseNode() {
        FlowRunSnapshotState stack = new FlowRunSnapshotState();
        stack.push("n1", "snap-a");
        stack.push("n2", "snap-b");

        String id = ResumeContinuationPlanner.resolveSnapshotId(
                ResumeDecision.builder().decision(ResumeDecision.RESTORE_AND_RETRY).build(),
                stack, "n2");
        assertEquals("snap-b", id);
    }

    /**
     * 前提：await_input + continueWithInput。
     * 期望：COMPLETE_NODE；带 completionAssigns。
     */
    @Test
    @Order(5)
    @DisplayName("continueWithInput 使用 COMPLETE_NODE")
    void plan_continueWithInput_usesCompleteNode() {
        RunExecutionState state = RunExecutionState.builder()
                .pauseNodeId("in1")
                .incomingEdgeId("e0")
                .pauseReason(RunExecutionState.PAUSE_REASON_AWAIT_INPUT)
                .build();
        List<?> assigns = java.util.List.of(java.util.Map.of("name", "code", "after", "1"));
        ResumeContinuationPlanner.PlannedResume planned = planner.plan(
                ResumeDecision.builder()
                        .decision(ResumeDecision.CONTINUE_WITH_INPUT)
                        .completionAssigns(assigns)
                        .build(),
                state, loadGraph("flow/linear-run-graph.json"), new FlowRunSnapshotState(),
                TestProjectEnv.builder().build(), 1L);

        assertNull(planned.getRestoreStep());
        assertEquals(RunContinuation.ResumeMode.COMPLETE_NODE, planned.getContinuation().getResumeMode());
        assertEquals("in1", planned.getContinuation().getStartNodeId());
        assertEquals(assigns, planned.getContinuation().getCompletionAssigns());
    }

    /**
     * 前提：await_input 但 decision=skip。
     * 期望：TF_RUN_RESUME_INVALID。
     */
    @Test
    @Order(6)
    @DisplayName("await_input 拒绝 skip")
    void plan_awaitInput_rejectsSkip() {
        RunExecutionState state = RunExecutionState.builder()
                .pauseNodeId("in1")
                .pauseReason(RunExecutionState.PAUSE_REASON_AWAIT_INPUT)
                .build();
        com.qualitest.flow.exception.FlowExecutionException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.qualitest.flow.exception.FlowExecutionException.class,
                        () -> planner.plan(
                                ResumeDecision.builder().decision(ResumeDecision.SKIP).build(),
                                state, loadGraph("flow/linear-run-graph.json"), new FlowRunSnapshotState(),
                                TestProjectEnv.builder().build(), 1L));
        assertEquals(com.qualitest.flow.exception.FlowErrorCode.TF_RUN_RESUME_INVALID, ex.getErrorCode());
    }

    private static GraphJson loadGraph(String path) {
        try (InputStream in = ResumeContinuationPlannerTest.class.getClassLoader().getResourceAsStream(path)) {
            return GraphJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
