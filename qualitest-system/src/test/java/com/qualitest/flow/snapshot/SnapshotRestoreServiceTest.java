package com.qualitest.flow.snapshot;

import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * 测 SnapshotRestoreService：续跑时的数据还原与审计步生成。
 * 边界：Mock DbSnapshotAdapter，不访问真实 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SnapshotRestoreServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotRestoreServiceTest {

    private final DbSnapshotAdapter adapter = mock(DbSnapshotAdapter.class);
    private final SnapshotRestoreService service = new SnapshotRestoreService(adapter, new StepResultWriter());

    /**
     * 前提：环境允许还原且 adapter 成功。
     * 期望：调用 restore；返回 nodeType=restore 的审计步，含 snapshotId。
     */
    @Test
    @Order(1)
    void restore_success() {
        begin("restore_success");
        doNothing().when(adapter).restore(anyString(), anyString(), anyLong());
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8081")
                .allowDestructiveReset(1)
                .build();

        var step = service.restore(env, 1L, "snap-1", "n1");
        assertEquals(StepResultWriter.NODE_TYPE_RESTORE, step.getNodeType());
        assertEquals("snap-1", step.getSnapshot().get("snapshotId"));
        log("restoreStep nodeType=" + step.getNodeType() + " snapshotId=snap-1");
        end("restore_success");
    }

    /**
     * 前提：allowDestructiveReset=0。
     * 期望：返回 null，不调 adapter。
     */
    @Test
    @Order(2)
    void restore_silentSkipWhenNotAllowed() {
        begin("restore_silentSkipWhenNotAllowed");
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8081")
                .allowDestructiveReset(0)
                .build();
        assertNull(service.restore(env, 1L, "snap-1", "n1"));
        log("restoreStep=null (silent skip)");
        end("restore_silentSkipWhenNotAllowed");
    }
}
