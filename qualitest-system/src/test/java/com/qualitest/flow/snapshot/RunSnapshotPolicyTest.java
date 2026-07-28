package com.qualitest.flow.snapshot;

import com.qualitest.flow.context.ResolvedRunScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 RunSnapshotPolicy：运行场景上的失败/快照策略解析与判定。
 * 边界：纯函数，仅依赖 ResolvedRunScenario 字段，无 DB / HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunSnapshotPolicyTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunSnapshotPolicyTest {

    /**
     * 前提：fromScenario 传入大小写混合的 onNodeFailure / onSnapshotFailure。
     * 期望：归一后 prompt 暂停节点失败，continue 在快照失败时继续。
     */
    @Test
    @Order(1)
    @DisplayName("归一化场景策略大小写")
    void fromScenario_normalizesCase() {
        RunSnapshotPolicy policy = RunSnapshotPolicy.fromScenario(ResolvedRunScenario.builder()
                .onNodeFailure("Prompt")
                .onSnapshotFailure("CONTINUE")
                .build());
        assertTrue(policy.shouldPauseOnNodeFailure());
        assertTrue(policy.shouldContinueOnSnapshotFailure());
        assertFalse(policy.shouldAbortOnSnapshotFailure());
    }

    /**
     * 前提：使用 defaults()，场景字段未配置。
     * 期望：节点失败不暂停；checkpoint 失败 abort。
     */
    @Test
    @Order(2)
    @DisplayName("defaults 节点不暂停且快照 abort")
    void defaults_failAndAbort() {
        RunSnapshotPolicy policy = RunSnapshotPolicy.defaults();
        assertFalse(policy.shouldPauseOnNodeFailure());
        assertTrue(policy.shouldAbortOnSnapshotFailure());
    }
}
