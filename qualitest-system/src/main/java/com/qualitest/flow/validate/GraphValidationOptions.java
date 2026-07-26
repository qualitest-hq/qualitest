package com.qualitest.flow.validate;

import lombok.Builder;
import lombok.Getter;

/**
 * 图校验选项。Staging 分批确认时可延后拓扑结构类规则（如开始节点唯一）。
 */
@Getter
@Builder
public class GraphValidationOptions {

    /** 为 true 时不校验开始节点唯一性，改以 warning 提示 */
    @Builder.Default
    private final boolean deferTopologyStructureRules = false;

    public static GraphValidationOptions full() {
        return GraphValidationOptions.builder().deferTopologyStructureRules(false).build();
    }

    public static GraphValidationOptions stagingPartialConfirm() {
        return GraphValidationOptions.builder().deferTopologyStructureRules(true).build();
    }
}
