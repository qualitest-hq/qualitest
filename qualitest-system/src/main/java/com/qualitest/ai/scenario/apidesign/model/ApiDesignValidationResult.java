package com.qualitest.ai.scenario.apidesign.model;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 对 ApiDesignPatch 做规范化后的校验结论。
 */
@Getter
@Builder
public class ApiDesignValidationResult {

    /** 是否通过校验且至少有一条有效 change */
    @Builder.Default
    private final boolean ok = true;

    /** 阻断性错误（禁止键、缺字段、脚本违禁等） */
    @Builder.Default
    private final List<String> errors = new ArrayList<>();

    /** 非阻断提示（如剥离了与 type 不匹配的约束键） */
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
}
