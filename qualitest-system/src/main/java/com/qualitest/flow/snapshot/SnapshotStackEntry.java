package com.qualitest.flow.snapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Run 内快照栈的一条记录。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotStackEntry {

    /** 开启 snapshotBefore 的节点 id */
    private String nodeId;

    /** 该节点 checkpoint 返回的快照 id */
    private String snapshotId;
}
