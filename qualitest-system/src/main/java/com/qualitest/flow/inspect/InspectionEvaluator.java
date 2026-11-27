package com.qualitest.flow.inspect;

/**
 * 插件流检查的求值方式。
 * 写在插件说明书 {@code ui.inspections[].evaluator}；核心内置检查不设该字段（为 null），由平台自身逻辑直接产出问题。
 */
public enum InspectionEvaluator {
    /** 开跑时通过挂点调用插件能力，由插件回包 issues */
    INVOKE("invoke", false),
    /** 平台侧校验：项目挂载必填项是否齐全 */
    PROJECT_MOUNT_REQUIRED("platform.projectMount.required", true),
    /** 平台侧校验：图依赖该插件时，项目是否至少有一个环境启用了它 */
    GRAPH_REQUIRES_PLUGIN_ENABLED("platform.graph.requiresPluginEnabled", true);

    private final String code;
    private final boolean platform;

    InspectionEvaluator(String code, boolean platform) {
        this.code = code;
        this.platform = platform;
    }

    /** 说明书与接口中使用的字符串值 */
    public String getCode() {
        return code;
    }

    /**
     * 是否由质衡平台本地求值。
     * {@code true} 时不调用插件进程；{@code false} 时走 Invoke。
     */
    public boolean isPlatform() {
        return platform;
    }

    /**
     * 按字符串解析求值方式。
     * 空白或未知值返回 {@code null}，调用方应跳过该项，避免空跑。
     */
    public static InspectionEvaluator fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        for (InspectionEvaluator e : values()) {
            if (e.code.equals(key)) {
                return e;
            }
        }
        return null;
    }
}
