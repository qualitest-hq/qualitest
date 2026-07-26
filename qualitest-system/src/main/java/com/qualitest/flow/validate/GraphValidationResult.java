package com.qualitest.flow.validate;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图校验结果。
 * <p>
 * {@code ok === (errors.isEmpty())}；warnings 不影响 ok。
 */
@Getter
public class GraphValidationResult {

    private final boolean ok;

    /** 必须修复的问题 */
    private final List<String> errors;

    /** 可保存但建议处理的问题 */
    private final List<String> warnings;

    public GraphValidationResult(boolean ok, List<String> errors, List<String> warnings) {
        this.ok = ok;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /** 由 error / warning 列表构造，自动计算 ok */
    public static GraphValidationResult of(List<String> errors, List<String> warnings) {
        return new GraphValidationResult(errors.isEmpty(), errors, warnings);
    }

    /** 由 {@link ValidationIssue} 列表拆分 errors / warnings */
    public static GraphValidationResult fromIssues(List<ValidationIssue> issues) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if (issue.getLevel() == ValidationIssue.Level.ERROR) {
                errors.add(issue.getMessage());
            } else {
                warnings.add(issue.getMessage());
            }
        }
        return of(errors, warnings);
    }
}
