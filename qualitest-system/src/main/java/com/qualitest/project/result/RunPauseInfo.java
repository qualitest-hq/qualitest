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
import java.util.Map;

/**
 * Run 详情中 paused 状态的补充信息，供前端展示暂停原因与续跑操作。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunPauseInfo implements Serializable {

    /**
     * 暂停原因：node_failure（步骤失败）、snapshot_failure（checkpoint 失败）、
     * await_input（等待人工输入）。
     */
    private String pauseReason;
    /** 触发暂停的节点 id */
    private String pauseNodeId;
    /** 暂停时的当前节点 id（通常与 pauseNodeId 相同） */
    private String currentNodeId;

    /** 本 Run 已打的 checkpoint 栈（nodeId + snapshotId） */
    @Builder.Default
    private List<SnapshotStackItemResult> snapshotStack = new ArrayList<>();

    /** 进入 paused 的时间 */
    private Date pausedAt;

    /**
     * 当前允许的决策列表。
     * await_input 仅为 continueWithInput、abort；
     * 失败类暂停为 restoreAndRetry、retryInPlace、skip、abort。
     */
    @Builder.Default
    private List<String> availableDecisions = new ArrayList<>();

    /** await_input 时节点上的提示文案（data.prompt） */
    private String prompt;

    /**
     * await_input 时节点上的字段定义（data.fields），含 name/label/type/required/options 等，
     * 供前端按类型渲染表单。
     */
    @Builder.Default
    private List<Map<String, Object>> fields = new ArrayList<>();
}
