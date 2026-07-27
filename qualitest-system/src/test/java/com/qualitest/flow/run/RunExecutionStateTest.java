package com.qualitest.flow.run;

import com.qualitest.flow.snapshot.SnapshotStackEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 测 RunExecutionState：暂停时持久化到 run_execution_state 的 JSON 往返。
 * 边界：纯序列化，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunExecutionStateTest
 */
class RunExecutionStateTest {

    /**
     * 前提：状态含 nextStepIndex、暂停节点、快照栈与 context，再序列化反序列化。
     * 期望：字段完整保留。
     */
    @Test
    void jsonRoundTrip() {
        begin("jsonRoundTrip");
        RunExecutionState state = RunExecutionState.builder()
                .nextStepIndex(5)
                .pauseNodeId("n2")
                .pauseReason(RunExecutionState.PAUSE_REASON_NODE_FAILURE)
                .snapshotStack(List.of(new SnapshotStackEntry("n1", "snap-1")))
                .context(Map.of("flow", Map.of("code", 0)))
                .build();

        RunExecutionState restored = RunExecutionState.fromJson(state.toJson());
        assertNotNull(restored);
        assertEquals(5, restored.getNextStepIndex());
        assertEquals("n2", restored.getPauseNodeId());
        assertEquals(1, restored.getSnapshotStack().size());
        log("nextStepIndex=5 pauseNodeId=n2 stackSize=1");
        end("jsonRoundTrip");
    }
}
