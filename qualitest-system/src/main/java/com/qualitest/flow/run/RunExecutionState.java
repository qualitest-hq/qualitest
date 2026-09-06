package com.qualitest.flow.run;

import com.alibaba.fastjson2.JSON;
import com.qualitest.flow.snapshot.SnapshotStackEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Run 暂停时写入 test_flow_run.run_execution_state 的 JSON 结构。
 * 续跑时读出，用于恢复上下文、快照栈和下一 step_index。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunExecutionState {

    /** 业务节点失败导致暂停 */
    public static final String PAUSE_REASON_NODE_FAILURE = "node_failure";
    /** checkpoint 失败且策略为 prompt 导致暂停 */
    public static final String PAUSE_REASON_SNAPSHOT_FAILURE = "snapshot_failure";
    /**
     * Input 节点等待人工输入。
     * 可用决策仅为 continueWithInput（提交字段值）与 abort。
     */
    public static final String PAUSE_REASON_AWAIT_INPUT = "await_input";

    /** 下一落库步的 step_index */
    private long nextStepIndex;

    /** 暂停时待执行或刚失败的节点 id */
    private String currentNodeId;

    /** 续跑时该节点的入边 id */
    private String incomingEdgeId;

    /** 暂停原因：node_failure / snapshot_failure / await_input */
    private String pauseReason;

    /** 触发暂停的节点 id，通常与 currentNodeId 相同 */
    private String pauseNodeId;

    /** 本 Run 已打 checkpoint 的 nodeId + snapshotId 列表 */
    @Builder.Default
    private List<SnapshotStackEntry> snapshotStack = new ArrayList<>();

    /** 可恢复的 flow/env 等上下文片段 */
    private Map<String, Object> context;

    public static RunExecutionState fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, RunExecutionState.class);
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
