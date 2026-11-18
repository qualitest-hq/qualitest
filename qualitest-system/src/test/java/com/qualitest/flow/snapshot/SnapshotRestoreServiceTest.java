package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.plugin.snapshot.PluginSnapshotSupport;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 SnapshotRestoreService：续跑时经 onRestore 插件挂点还原。
 * 边界：Mock PluginSnapshotSupport，不访问真实 CLI。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SnapshotRestoreServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotRestoreServiceTest {

    private final PluginSnapshotSupport snapshotSupport = mock(PluginSnapshotSupport.class);
    private final SnapshotRestoreService service =
            new SnapshotRestoreService(snapshotSupport, new StepResultWriter());

    /**
     * 前提：环境允许还原且挂点已启用。
     * 期望：调用 restoreSave；返回 nodeType=restore 的审计步。
     */
    @Test
    @Order(1)
    @DisplayName("允许还原时返回 restore 审计步")
    void restore_success() {
        when(snapshotSupport.isRestoreProviderEnabled(anyLong())).thenReturn(true);
        when(snapshotSupport.describeCheckpointProvider(anyLong())).thenReturn("plugin:onCheckpoint");
        doNothing().when(snapshotSupport).restoreSave(any(), anyLong(), anyString(), anyString());
        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(12L)
                .envUrl("http://localhost:8801")
                .allowDestructiveReset(1)
                .build();

        var step = service.restore(env, 1L, "snap-1", "n1");
        assertEquals(StepResultWriter.NODE_TYPE_RESTORE, step.getNodeType());
        assertEquals("snap-1", step.getSnapshot().get("snapshotId"));
    }

    /**
     * 前提：allowDestructiveReset=0。
     * 期望：显式还原失败，不静默跳过。
     */
    @Test
    @Order(2)
    @DisplayName("不允许还原时显式失败")
    void restore_failsWhenNotAllowed() {
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8801")
                .allowDestructiveReset(0)
                .build();
        assertThrows(FlowExecutionException.class, () -> service.restore(env, 1L, "snap-1", "n1"));
    }
}
