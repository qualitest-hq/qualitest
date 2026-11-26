package com.qualitest.flow.validate;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图结构校验结果。
 * <p>
 * {@code ok === (errors.isEmpty())}；不确定项静默，不设 warning 档。
 */
@Getter
public class GraphValidationResult {

    private final boolean ok;

    /** 必须修复的问题 */
    private final List<String> errors;

    public GraphValidationResult(boolean ok, List<String> errors) {
        this.ok = ok;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /** 由 error 列表构造，自动计算 ok */
    public static GraphValidationResult of(List<String> errors) {
        List<String> errs = errors == null ? List.of() : errors;
        return new GraphValidationResult(errs.isEmpty(), errs);
    }

    /** 由 {@link ValidationIssue} 列表投影为 errors（仅 ERROR） */
    public static GraphValidationResult fromIssues(List<ValidationIssue> issues) {
        List<String> errors = new ArrayList<>();
        if (issues != null) {
            for (ValidationIssue issue : issues) {
                if (issue != null && issue.getLevel() == ValidationIssue.Level.ERROR) {
                    errors.add(issue.getMessage());
                }
            }
        }
        return of(errors);
    }
}
