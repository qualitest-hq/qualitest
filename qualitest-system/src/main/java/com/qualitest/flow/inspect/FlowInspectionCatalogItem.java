package com.qualitest.flow.inspect;

import lombok.Builder;
import lombok.Getter;

/**
 * 流检查目录接口返回的单条记录。
 * 在目录定义之上附加本项目生效后的严重性，供项目设置页展示与编辑。
 */
@Getter
@Builder
public class FlowInspectionCatalogItem {

    /** 生效诊断码 */
    private final String code;

    /** 包内短码；核心内置与 code 相同 */
    private final String shortCode;

    /** 展示名 */
    private final String name;

    /** 分组名 */
    private final String category;

    /** 所属插件 id；核心内置为 null */
    private final String pluginId;

    /** 求值方式字符串；核心内置为 null */
    private final String evaluator;

    /** Invoke 时使用的能力名；非 Invoke 为 null */
    private final String capability;

    /** 是否锁定为 ERROR */
    private final boolean locked;

    /** 目录默认严重性 */
    private final InspectionConfiguredSeverity defaultSeverity;

    /** 结合项目覆盖后的生效严重性 */
    private final InspectionConfiguredSeverity effectiveSeverity;
}
