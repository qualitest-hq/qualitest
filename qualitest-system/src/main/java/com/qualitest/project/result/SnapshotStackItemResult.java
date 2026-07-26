package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Run 详情 API 返回的快照栈单条：打快照时的节点 id 与被测方 snapshotId。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotStackItemResult implements Serializable {

    private String nodeId;
    private String snapshotId;
}
