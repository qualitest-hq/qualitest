package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SnapshotCheckpointService} 单元测试：节点前 checkpoint 尝试与快照栈压入。
 * <p>
 * Mock {@link DbSnapshotAdapter}，验证 maybeCheckpoint 在各类条件下的返回值。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=SnapshotCheckpointServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotCheckpointServiceTest {

    private final DbSnapshotAdapter adapter = mock(DbSnapshotAdapter.class);
    private final SnapshotCheckpointService service = new SnapshotCheckpointService(adapter);

    /**
     * 节点未开 snapshotBefore。
     * 期望：返回 null，不调 adapter。
     */
    @Test
    @Order(1)
    void maybeCheckpoint_skipsWhenSnapshotBeforeFalse() {
        begin("maybeCheckpoint_skipsWhenSnapshotBeforeFalse");
        GraphNode node = GraphNode.builder().id("n1").type("http").data(Map.of()).build();
        assertNull(service.maybeCheckpoint(node, 1L, env(), RunSnapshotPolicy.defaults(), new FlowRunSnapshotState()));
        log("attempt=null");
        end("maybeCheckpoint_skipsWhenSnapshotBeforeFalse");
    }

    /**
     * snapshotBefore=true 且环境允许还原。
     * 期望：adapter.snapshot 被调用；attempt 成功；栈顶 snapshotId=snap-1。
     */
    @Test
    @Order(2)
    void maybeCheckpoint_callsAdapterWhenEnabled() {
        begin("maybeCheckpoint_callsAdapterWhenEnabled");
        when(adapter.snapshot(any())).thenReturn(SnapshotRef.builder()
                .snapshotId("snap-1")
                .status("ready")
                .scope("tables")
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_SCOPE, Map.of(
                "scope", "tables",
                "tables", List.of("mall_order")
        ));
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();
        FlowRunSnapshotState state = new FlowRunSnapshotState();

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 99L, env(), RunSnapshotPolicy.defaults(), state);

        assertNotNull(attempt);
        assertTrue(attempt.isPassed());
        assertEquals("snap-1", state.peek().getSnapshotId());
        verify(adapter).snapshot(any());
        log("passed=true snapshotId=snap-1 stackSize=" + state.entries().size());
        end("maybeCheckpoint_callsAdapterWhenEnabled");
    }

    /**
     * onSnapshotFailure=prompt 且 adapter 抛错。
     * 期望：attempt 失败且 shouldPause=true，不 abort、不 continue。
     */
    @Test
    @Order(3)
    void maybeCheckpoint_onSnapshotFailurePrompt_shouldPause() {
        begin("maybeCheckpoint_onSnapshotFailurePrompt_shouldPause");
        when(adapter.snapshot(any())).thenThrow(new SnapshotException(FlowErrorCode.TF_SNAPSHOT_FAILED, "down"));

        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        RunSnapshotPolicy policy = RunSnapshotPolicy.builder()
                .onSnapshotFailure(RunSnapshotPolicy.ON_SNAPSHOT_FAILURE_PROMPT)
                .build();

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 1L, env(), policy, new FlowRunSnapshotState());

        assertTrue(attempt.isShouldPause());
        assertTrue(attempt.shouldContinueDespiteFailure() == false);
        assertTrue(attempt.isAbortRun() == false);
        log("shouldPause=true abortRun=false");
        end("maybeCheckpoint_onSnapshotFailurePrompt_shouldPause");
    }

    /**
     * allowDestructiveReset=0 但节点开了 snapshotBefore。
     * 期望：静默跳过，返回 null。
     */
    @Test
    @Order(4)
    void maybeCheckpoint_silentSkipWhenResetNotAllowed() {
        begin("maybeCheckpoint_silentSkipWhenResetNotAllowed");
        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 1L, TestProjectEnv.builder().envUrl("http://localhost:8081").allowDestructiveReset(0).build(),
                RunSnapshotPolicy.defaults(), new FlowRunSnapshotState());

        assertNull(attempt);
        log("attempt=null (env reset not allowed)");
        end("maybeCheckpoint_silentSkipWhenResetNotAllowed");
    }

    private static TestProjectEnv env() {
        return TestProjectEnv.builder()
                .envUrl("http://localhost:8081")
                .envName("test")
                .allowDestructiveReset(1)
                .build();
    }
}
