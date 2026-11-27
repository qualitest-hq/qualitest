package com.qualitest.flow.inspect;

import com.qualitest.flow.validate.FlowIssueCodes;

import java.util.List;

/**
 * 平台内置流检查目录。
 * 不含插件声明的项；插件项由已安装包说明书解析后追加。
 */
public final class BuiltinFlowInspections {

    private BuiltinFlowInspections() {}

    /**
     * 全部内置项。
     * {@code GRAPH_JSON_INVALID} 锁定为 ERROR；其余可由项目配置改级或关闭。
     */
    public static final List<FlowInspectionDef> ALL = List.of(
            def(FlowIssueCodes.GRAPH_JSON_INVALID, "图 JSON 无法解析", "图结构",
                    InspectionConfiguredSeverity.ERROR, true),
            def(FlowIssueCodes.GRAPH_STRUCTURE, "图结构 / 字段错误", "图结构",
                    InspectionConfiguredSeverity.ERROR, false),
            def(FlowIssueCodes.ENTRY_ROUTE_CONFLICT, "开放 HTTP 路由冲突", "图结构",
                    InspectionConfiguredSeverity.ERROR, false),
            def(FlowIssueCodes.ASSERT_PATH, "断言 / 条件路径错误", "图结构",
                    InspectionConfiguredSeverity.ERROR, false),
            def(FlowIssueCodes.RUN_READINESS_AUTH, "鉴权 / 登录抽取 / HTTP 必填", "运行就绪",
                    InspectionConfiguredSeverity.ERROR, false),
            def(FlowIssueCodes.API_MISSING, "HTTP 未绑定或接口已删", "API 语义",
                    InspectionConfiguredSeverity.ERROR, false),
            def(FlowIssueCodes.ORPHAN_PARAM, "测值参数在接口中已不存在", "API 语义",
                    InspectionConfiguredSeverity.WARNING, false),
            def(FlowIssueCodes.EXTRACT_PATH_MISSING, "抽取路径对不上响应结构", "API 语义",
                    InspectionConfiguredSeverity.WARNING, false),
            def(FlowIssueCodes.INSPECT_INVOKE_FAILED, "插件检查 Invoke 失败", "插件",
                    InspectionConfiguredSeverity.WARNING, false)
    );

    /** 组装一条无求值器的内置目录项 */
    private static FlowInspectionDef def(
            String code,
            String name,
            String category,
            InspectionConfiguredSeverity severity,
            boolean locked) {
        return FlowInspectionDef.builder()
                .code(code)
                .shortCode(code)
                .name(name)
                .category(category)
                .defaultSeverity(severity)
                .locked(locked)
                .build();
    }
}
