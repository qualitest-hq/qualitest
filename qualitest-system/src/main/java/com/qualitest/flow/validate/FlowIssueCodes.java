package com.qualitest.flow.validate;

/**
 * 流图诊断码常量。
 * 接口与单测按码识别问题种类，不依赖中文文案。
 */
public final class FlowIssueCodes {

    private FlowIssueCodes() {}

    /** 开放 HTTP 入口的 method+path 在本流或跨流发生冲突 */
    public static final String ENTRY_ROUTE_CONFLICT = "ENTRY_ROUTE_CONFLICT";

    /**
     * 图 JSON 文本无法解析成对象（解析地板，非流检查目录项；不能被 inspection_profile 改级/关闭）。
     */
    public static final String GRAPH_JSON_INVALID = "GRAPH_JSON_INVALID";

    /** 图结构或字段类错误的通用码 */
    public static final String GRAPH_STRUCTURE = "GRAPH_STRUCTURE";

    /** 断言或条件路径设计错误 */
    public static final String ASSERT_PATH = "ASSERT_PATH";

    /** 鉴权 / 登录键碰撞等运行就绪问题（不含 HTTP 必填） */
    public static final String RUN_READINESS_AUTH = "RUN_READINESS_AUTH";

    /** 成功路径 HTTP 缺必填测值 */
    public static final String RUN_READINESS_HTTP_PARAM = "RUN_READINESS_HTTP_PARAM";

    /** HTTP 节点未绑定接口，或绑定的接口已不存在 */
    public static final String API_MISSING = "API_MISSING";

    /** 测值覆盖的参数名在接口请求参数中已不存在 */
    public static final String ORPHAN_PARAM = "ORPHAN_PARAM";

    /** 响应抽取路径在接口响应结构中找不到 */
    public static final String EXTRACT_PATH_MISSING = "EXTRACT_PATH_MISSING";

    /**
     * 插件流检查 Invoke 失败。
     * 平台整次调度失败时用本短码；单个插件失败时通常写成 {@code 插件id/INSPECT_INVOKE_FAILED}。
     */
    public static final String INSPECT_INVOKE_FAILED = "INSPECT_INVOKE_FAILED";
}
