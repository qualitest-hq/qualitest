package com.qualitest.flow.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 图遍历续跑入口：从哪个节点开始、以何种模式继续。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunContinuation {

    /**
     * 续跑模式。
     */
    public enum ResumeMode {
        /** 从头或从指定节点正常执行 Handler */
        NORMAL,
        /** 从当前节点重试（会再次触发节点前 checkpoint 钩子） */
        RETRY_NODE,
        /** 不执行当前节点，记 skipped 后沿出边前进 */
        SKIP_NODE,
        /** 不执行 Handler：flow 已在续跑前写好，记 passed（可带 assigns）后沿出边前进 */
        COMPLETE_NODE
    }

    /** 续跑起点节点 id；空表示从图唯一开始节点起步 */
    private String startNodeId;
    /** 起点节点的入边 id，写入步骤 edgeId */
    private String incomingEdgeId;
    @Builder.Default
    private ResumeMode resumeMode = ResumeMode.NORMAL;

    /**
     * COMPLETE_NODE 时写入步骤 assigns 的明细（人工输入写入结果）。
     * 其它模式为 null。
     */
    private Object completionAssigns;

    /** 全新执行（非续跑） */
    public static RunContinuation fresh() {
        return RunContinuation.builder().resumeMode(ResumeMode.NORMAL).build();
    }

    /** 未指定 startNodeId 时视为从头跑 */
    public boolean isFreshStart() {
        return startNodeId == null || startNodeId.isBlank();
    }
}
