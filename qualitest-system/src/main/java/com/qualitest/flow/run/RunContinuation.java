package com.qualitest.flow.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 图执行续跑参数：指定从哪个节点、以何种模式继续。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunContinuation {

    public enum ResumeMode {
        /** 正常从头或指定节点执行 */
        NORMAL,
        /** 重试当前节点（含 checkpoint 钩子） */
        RETRY_NODE,
        /** 跳过当前节点，直接解析下一节点 */
        SKIP_NODE
    }

    private String startNodeId;
    private String incomingEdgeId;
    @Builder.Default
    private ResumeMode resumeMode = ResumeMode.NORMAL;

    public static RunContinuation fresh() {
        return RunContinuation.builder().resumeMode(ResumeMode.NORMAL).build();
    }

    public boolean isFreshStart() {
        return startNodeId == null || startNodeId.isBlank();
    }
}
