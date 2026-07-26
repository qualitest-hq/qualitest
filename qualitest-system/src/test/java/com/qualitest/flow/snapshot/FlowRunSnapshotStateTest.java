package com.qualitest.flow.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowRunSnapshotStateTest {

    @Test
    void truncateAfter_removesLaterEntries() {
        FlowRunSnapshotState state = new FlowRunSnapshotState();
        state.push("n1", "snap-1");
        state.push("n2", "snap-2");
        state.push("n3", "snap-3");

        assertTrue(state.truncateAfter("snap-2"));
        assertEquals(2, state.entries().size());
        assertEquals("snap-2", state.peek().getSnapshotId());
    }

    @Test
    void jsonRoundTrip() {
        FlowRunSnapshotState state = new FlowRunSnapshotState();
        state.push("n1", "snap-a");
        String json = state.toJson();

        FlowRunSnapshotState restored = FlowRunSnapshotState.fromJson(json);
        assertEquals(1, restored.entries().size());
        assertEquals("snap-a", restored.findBySnapshotId("snap-a").orElseThrow().getSnapshotId());
    }
}
