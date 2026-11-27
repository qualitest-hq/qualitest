package com.qualitest.flow.inspect;

import lombok.Builder;
import lombok.Getter;

/**
 * 流检查目录中的一项定义。
 * 来源可以是平台内置，也可以是已安装插件在说明书 {@code ui.inspections} 中声明的项。
 */
@Getter
@Builder
public class FlowInspectionDef {

    /**
     * 生效诊断码。
     * 核心内置即短码本身；插件项为 {@code 插件id/短码}。
     */
    private final String code;

    /** 设置页与提示中展示的名称 */
    private final String name;

    /** 分组名，例如图结构、运行就绪、API 语义、或插件自定义分类 */
    private final String category;

    /** 未做项目覆盖时的默认严重性 */
    private final InspectionConfiguredSeverity defaultSeverity;

    /**
     * 是否锁定。
     * 为 true 时严重性固定为 ERROR，项目配置不可关闭或降级。
     */
    private final boolean locked;

    /** 所属插件 id；核心内置项为 null */
    private final String pluginId;

    /**
     * 求值方式。
     * 核心内置为 null（由对应收集器直接出问题）；插件项为枚举值。
     */
    private final InspectionEvaluator evaluator;

    /**
     * 插件能力名。
     * 仅当求值方式为 Invoke 时需要，用于调度对应能力。
     */
    private final String capability;

    /**
     * 包内短码。
     * 插件项为说明书中的 code；核心内置与 {@link #code} 相同。
     */
    private final String shortCode;
}
