package com.qualitest.flow.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowRunSnapshotState：运行期快照栈的截断与 JSON 往返。
 * 边界：纯内存结构，无 DB / HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunSnapshotStateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunSnapshotStateTest {

    /**
     * 前提：栈上依次有 snap-1 / snap-2 / snap-3。
     * 期望：truncateAfter(snap-2) 后仅保留到 snap-2。
     */
    @Test
    @Order(1)
    @DisplayName("截断：保留到指定 snapshot 并去掉其后条目")
    void truncateAfter_removesLaterEntries() {
        FlowRunSnapshotState state = new FlowRunSnapshotState();
        state.push("n1", "snap-1");
        state.push("n2", "snap-2");
        state.push("n3", "snap-3");

        assertTrue(state.truncateAfter("snap-2"));
        assertEquals(2, state.entries().size());
        assertEquals("snap-2", state.peek().getSnapshotId());
    }

    /**
     * 前提：栈上有一条 snap-a，序列化为 JSON 再还原。
     * 期望：条目数与 snapshotId 一致。
     */
    @Test
    @Order(2)
    @DisplayName("JSON 往返：条目数与 snapshotId 一致")
    void jsonRoundTrip() {
        FlowRunSnapshotState state = new FlowRunSnapshotState();
        state.push("n1", "snap-a");
        String json = state.toJson();

        FlowRunSnapshotState restored = FlowRunSnapshotState.fromJson(json);
        assertEquals(1, restored.entries().size());
        assertEquals("snap-a", restored.findBySnapshotId("snap-a").orElseThrow().getSnapshotId());
    }
}
