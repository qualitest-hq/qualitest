package com.qualitest.flow.validate;

import lombok.Builder;
import lombok.Getter;

/**
 * 图结构校验选项。
 */
@Getter
@Builder
public class GraphValidationOptions {

    /**
     * 为 true 时：开始节点唯一性失败写入 warnings 而非 errors。
     * 用于尚有未确认连线、合并预览图拓扑暂不可信的场景。
     */
    @Builder.Default
    private final boolean deferTopologyStructureRules = false;

    /** 完整校验：开始节点等问题一律硬拦。 */
    public static GraphValidationOptions full() {
        return GraphValidationOptions.builder().deferTopologyStructureRules(false).build();
    }

    /** Staging 分批确认：延后开始节点唯一性硬拦。 */
    public static GraphValidationOptions stagingPartialConfirm() {
        return GraphValidationOptions.builder().deferTopologyStructureRules(true).build();
    }
}
