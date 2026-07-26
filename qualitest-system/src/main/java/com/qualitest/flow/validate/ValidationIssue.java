package com.qualitest.flow.validate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 图校验单条问题。
 */
@Getter
@AllArgsConstructor
public class ValidationIssue {

    public enum Level {
        /** 阻止保存或 Run */
        ERROR,
        /** 允许保存，Run 或导入时可能失败/需迁移 */
        WARNING
    }

    private final Level level;

    private final String message;
}
