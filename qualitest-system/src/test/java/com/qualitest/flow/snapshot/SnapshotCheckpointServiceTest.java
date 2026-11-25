package com.qualitest.flow.snapshot;

import com.qualitest.flow.model.GraphNode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.snapshot.PluginSnapshotSupport;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 SnapshotCheckpointService：节点前 checkpoint 经 onCheckpoint 插件挂点。
 * 边界：Mock PluginSnapshotSupport，不访问真实 CLI。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SnapshotCheckpointServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotCheckpointServiceTest {

    private final PluginSnapshotSupport snapshotSupport = mock(PluginSnapshotSupport.class);
    private final SnapshotCheckpointService service = new SnapshotCheckpointService(snapshotSupport);

    /**
     * 前提：节点未开 snapshotBefore。
     * 期望：返回 null。
     */
    @Test
    @Order(1)
    @DisplayName("未开 snapshotBefore 时跳过 checkpoint")
    void maybeCheckpoint_skipsWhenSnapshotBeforeFalse() {
        GraphNode node = GraphNode.builder().id("n1").type("http").data(Map.of()).build();
        assertNull(service.maybeCheckpoint(node, 1L, env(), RunSnapshotPolicy.defaults(), new FlowRunSnapshotState()));
    }

    /**
     * 前提：snapshotBefore=true、挂点已启用；createSave 返回 snap-1。
     * 期望：attempt 成功；栈顶 snapshotId=snap-1。
     */
    @Test
    @Order(2)
    @DisplayName("开启后经插件压栈成功")
    void maybeCheckpoint_callsPluginWhenEnabled() {
        when(snapshotSupport.isCheckpointProviderEnabled(anyLong())).thenReturn(true);
        when(snapshotSupport.describeCheckpointProvider(anyLong())).thenReturn("plugin:onCheckpoint");
        when(snapshotSupport.createSave(any(), anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("snap-1");

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
        verify(snapshotSupport).createSave(any(), eq(99L), eq("n1"), anyString(), eq(1));
    }

    /**
     * 前提：历史 onSnapshotFailure=prompt（现归 abort），插件调用失败。
     * 期望：abortRun=true，不 continue。
     */
    @Test
    @Order(3)
    @DisplayName("快照失败且历史 prompt 归 abort")
    void maybeCheckpoint_legacyPrompt_aborts() {
        when(snapshotSupport.isCheckpointProviderEnabled(anyLong())).thenReturn(true);
        when(snapshotSupport.describeCheckpointProvider(anyLong())).thenReturn("plugin:onCheckpoint");
        when(snapshotSupport.createSave(any(), anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new PluginException(PluginErrorCode.INVOKE_FAILED, "down"));

        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        RunSnapshotPolicy policy = RunSnapshotPolicy.compose(null, "prompt");

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 1L, env(), policy, new FlowRunSnapshotState());

        assertTrue(attempt.isAbortRun());
        assertFalse(attempt.shouldContinueDespiteFailure());
    }

    /**
     * 前提：snapshotBefore=true，但 allowDestructiveReset=0。
     * 期望：静默跳过，返回 null。
     */
    @Test
    @Order(4)
    @DisplayName("环境不允许还原时静默跳过")
    void maybeCheckpoint_silentSkipWhenResetNotAllowed() {
        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 1L, TestProjectEnv.builder().envUrl("http://localhost:8801").allowDestructiveReset(0).build(),
                RunSnapshotPolicy.defaults(), new FlowRunSnapshotState());

        assertNull(attempt);
    }

    /**
     * 前提：允许还原但未启用 onCheckpoint 插件。
     * 期望：失败 attempt，不暗门回落。
     */
    @Test
    @Order(5)
    @DisplayName("未启用插件时 checkpoint 失败")
    void maybeCheckpoint_failsWhenPluginDisabled() {
        when(snapshotSupport.isCheckpointProviderEnabled(anyLong())).thenReturn(false);
        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true);
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        CheckpointAttempt attempt = service.maybeCheckpoint(
                node, 1L, env(), RunSnapshotPolicy.defaults(), new FlowRunSnapshotState());

        assertNotNull(attempt);
        assertFalse(attempt.isPassed());
    }

    private static TestProjectEnv env() {
        return TestProjectEnv.builder()
                .testProjectEnvId(12L)
                .testProjectId(1L)
                .envUrl("http://localhost:8801")
                .envName("test")
                .allowDestructiveReset(1)
                .build();
    }
}
