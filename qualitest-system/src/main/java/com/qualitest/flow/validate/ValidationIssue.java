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
        /** 画布标错 / 开跑硬拦；不拦保存 */
        ERROR,
        /** 画布提示；不拦 Run */
        WARNING
    }

    private final Level level;

    private final String message;
}
