package com.qualitest.flow.snapshot;

import com.qualitest.flow.context.ResolvedRunScenario;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RunSnapshotPolicy} 单元测试：运行场景上的失败/快照策略解析与判定。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=RunSnapshotPolicyTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunSnapshotPolicyTest {

    /**
     * fromScenario 传入大小写混合的取值。
     * 期望：prompt → shouldPauseOnNodeFailure；continue → shouldContinueOnSnapshotFailure。
     */
    @Test
    @Order(1)
    void fromScenario_normalizesCase() {
        begin("fromScenario_normalizesCase");
        RunSnapshotPolicy policy = RunSnapshotPolicy.fromScenario(ResolvedRunScenario.builder()
                .onNodeFailure("Prompt")
                .onSnapshotFailure("CONTINUE")
                .build());
        assertTrue(policy.shouldPauseOnNodeFailure());
        assertTrue(policy.shouldContinueOnSnapshotFailure());
        assertFalse(policy.shouldAbortOnSnapshotFailure());
        log("pauseOnNodeFailure=true continueOnSnapshotFailure=true");
        end("fromScenario_normalizesCase");
    }

    /**
     * 默认策略未配置场景字段。
     * 期望：节点失败不暂停；checkpoint 失败 abort。
     */
    @Test
    @Order(2)
    void defaults_failAndAbort() {
        begin("defaults_failAndAbort");
        RunSnapshotPolicy policy = RunSnapshotPolicy.defaults();
        assertFalse(policy.shouldPauseOnNodeFailure());
        assertTrue(policy.shouldAbortOnSnapshotFailure());
        log("defaultOnNodeFailure=fail defaultOnSnapshotFailure=abort");
        end("defaults_failAndAbort");
    }
}
