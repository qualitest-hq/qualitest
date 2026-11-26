package com.qualitest.flow.validate;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图校验 API 出参：统一 issues 列表。
 */
@Getter
public class FlowGraphValidateResult {

    private final List<FlowIssue> issues;

    private FlowGraphValidateResult(List<FlowIssue> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public static FlowGraphValidateResult of(List<FlowIssue> issues) {
        return new FlowGraphValidateResult(issues != null ? issues : List.of());
    }

    /** 是否存在 severity=error 的问题 */
    public boolean hasErrors() {
        for (FlowIssue issue : issues) {
            if (issue != null && issue.isError()) {
                return true;
            }
        }
        return false;
    }
}
