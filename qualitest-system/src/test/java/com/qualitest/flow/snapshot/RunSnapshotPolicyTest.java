package com.qualitest.flow.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.qualitest.flow.run.GraphWalker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 RunSnapshotPolicy：compose 合成入口 maxSteps / 存档失败策略，以及步数裁剪。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunSnapshotPolicyTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunSnapshotPolicyTest {

    /**
     * 前提：compose 传入 continue 与历史 prompt。
     * 期望：continue 生效；prompt 归 abort。
     */
    @Test
    @Order(1)
    @DisplayName("compose 归一化存档失败策略")
    void compose_normalizesSnapshotFailure() {
        RunSnapshotPolicy continuePolicy = RunSnapshotPolicy.compose(null, "CONTINUE");
        assertTrue(continuePolicy.shouldContinueOnSnapshotFailure());
        assertFalse(continuePolicy.shouldAbortOnSnapshotFailure());

        RunSnapshotPolicy promptAsAbort = RunSnapshotPolicy.compose(50, "prompt");
        assertTrue(promptAsAbort.shouldAbortOnSnapshotFailure());
        assertEquals(50, promptAsAbort.getMaxSteps());
    }

    /**
     * 前提：使用 defaults()。
     * 期望：checkpoint 失败 abort；步数默认。
     */
    @Test
    @Order(2)
    @DisplayName("defaults 快照 abort 且步数默认")
    void defaults_abortAndDefaultMaxSteps() {
        RunSnapshotPolicy policy = RunSnapshotPolicy.defaults();
        assertTrue(policy.shouldAbortOnSnapshotFailure());
        assertEquals(GraphWalker.DEFAULT_MAX_STEPS, policy.getMaxSteps());
    }

    /**
     * 前提：compose maxSteps=80；单独解析 null、0、5000。
     * 期望：80 原样；null 默认；0→1；超大裁封顶。
     */
    @Test
    @Order(3)
    @DisplayName("maxSteps 传入并裁剪到合法范围")
    void compose_resolvesMaxSteps() {
        assertEquals(80, RunSnapshotPolicy.compose(80, null).getMaxSteps());
        assertEquals(1, RunSnapshotPolicy.resolveMaxSteps(0));
        assertEquals(GraphWalker.MAX_STEPS_CEILING, RunSnapshotPolicy.resolveMaxSteps(5000));
        assertEquals(GraphWalker.DEFAULT_MAX_STEPS, RunSnapshotPolicy.resolveMaxSteps(null));
    }
}
