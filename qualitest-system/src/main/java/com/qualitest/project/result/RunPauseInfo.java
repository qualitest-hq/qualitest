package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Run 详情 API 中 paused 状态的补充信息。
 * 包含暂停原因、节点、可选快照栈、可选决策列表，供前端展示续跑操作。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunPauseInfo implements Serializable {

    /** node_failure / snapshot_failure */
    private String pauseReason;
    private String pauseNodeId;
    private String currentNodeId;

    @Builder.Default
    private List<SnapshotStackItemResult> snapshotStack = new ArrayList<>();

    private Date pausedAt;

    /** 前端可展示的 decision 取值列表 */
    @Builder.Default
    private List<String> availableDecisions = new ArrayList<>();
}
