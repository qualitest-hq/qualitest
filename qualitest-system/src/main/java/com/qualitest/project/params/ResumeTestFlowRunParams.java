package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 恢复 paused Run 的 HTTP 请求体。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeTestFlowRunParams implements Serializable {

    /**
     * 决策：restoreAndRetry / retryInPlace / skip / abort / continueWithInput。
     */
    private String decision;

    /**
     * restoreAndRetry 时的快照 id；缺省由引擎按暂停节点或栈顶选取。
     */
    private String snapshotId;

    /**
     * continueWithInput 时提交的人工输入：字段 name → 值。
     */
    private Map<String, Object> inputs;
}
