package com.qualitest.flow.validate;

import lombok.Builder;
import lombok.Getter;

/**
 * 图结构校验选项：控制校验严格程度与拓扑规则是否延后。
 */
@Getter
@Builder
public class GraphValidationOptions {

    /**
     * true：跳过开始节点等拓扑规则（不产出 issue）。
     * 用于尚有未确认连线、当前合并预览拓扑可能暂时不准。
     */
    @Builder.Default
    private final boolean deferTopologyStructureRules = false;

    /**
     * true：只检查落库地板（节点 id、边端点），
     * 不检查字段完整性、开始节点、断言等内容细节。
     */
    @Builder.Default
    private final boolean persistMinimalOnly = false;

    /** 完整结构校验：开始节点等问题一律记为错误。 */
    public static GraphValidationOptions full() {
        return GraphValidationOptions.builder()
                .deferTopologyStructureRules(false)
                .persistMinimalOnly(false)
                .build();
    }

    /**
     * 延后开始节点等拓扑硬规则（submit 单单元 / 画布有 pending 边时）。
     * 避免半成品图误报；开跑仍用 {@link #full()}。
     */
    public static GraphValidationOptions deferTopology() {
        return GraphValidationOptions.builder()
                .deferTopologyStructureRules(true)
                .persistMinimalOnly(false)
                .build();
    }

    /** 写库地板：只查节点缺 id / id 重复、边缺 source/target（不硬拦保存）。 */
    public static GraphValidationOptions persistMinimal() {
        return GraphValidationOptions.builder()
                .deferTopologyStructureRules(false)
                .persistMinimalOnly(true)
                .build();
    }
}
