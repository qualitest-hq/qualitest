package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 恢复 paused Run 的请求参数。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeTestFlowRunParams implements Serializable {

    /**
     * restoreAndRetry / retryInPlace / skip / abort
     */
    private String decision;

    /**
     * restoreAndRetry 时指定快照 id；缺省时取失败节点或栈顶 checkpoint
     */
    private String snapshotId;
}
