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
     * true：开始节点「不唯一 / 缺失」只记为警告，不记为错误。
     * 用于尚有未确认连线、当前合并预览拓扑可能暂时不准。
     */
    @Builder.Default
    private final boolean deferTopologyStructureRules = false;

    /**
     * true：只检查能否安全落库的最小结构（节点 id、边端点），
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

    /** 分批确认预览：开始节点问题降为警告，避免未确认边造成误报。 */
    public static GraphValidationOptions stagingPartialConfirm() {
        return GraphValidationOptions.builder()
                .deferTopologyStructureRules(true)
                .persistMinimalOnly(false)
                .build();
    }

    /** 写库地板：只拦节点缺 id / id 重复、边缺 source/target。 */
    public static GraphValidationOptions persistMinimal() {
        return GraphValidationOptions.builder()
                .deferTopologyStructureRules(false)
                .persistMinimalOnly(true)
                .build();
    }
}
