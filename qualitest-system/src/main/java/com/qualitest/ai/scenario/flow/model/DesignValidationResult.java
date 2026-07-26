package com.qualitest.ai.scenario.flow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 设计结果中的图结构校验摘要。
 * <p>
 * {@code ok} 为 true 表示 errors 为空；warnings 不阻断保存但建议用户处理。
 */
@Getter
@Builder
public class DesignValidationResult {

    private final boolean ok;
    /** 必须修复才能保存的问题 */
    private final List<String> errors;
    /** 可保存但建议处理的问题 */
    private final List<String> warnings;
}
