package com.qualitest.flow.validate;

/**
 * 流图诊断码常量；测试与 API 契约只认 code，不认中文文案。
 */
public final class FlowIssueCodes {

    private FlowIssueCodes() {}

    /** 开放 HTTP method+path 本流或跨流冲突 */
    public static final String ENTRY_ROUTE_CONFLICT = "ENTRY_ROUTE_CONFLICT";

    /** 图 JSON 无法解析 */
    public static final String GRAPH_JSON_INVALID = "GRAPH_JSON_INVALID";

    /** 图结构 / 字段类错误（过渡期未细分时的兜底码） */
    public static final String GRAPH_STRUCTURE = "GRAPH_STRUCTURE";

    /** 断言 / 条件路径设计错误 */
    public static final String ASSERT_PATH = "ASSERT_PATH";

    /** 鉴权 / 登录抽取 / HTTP 必填等就绪风险 */
    public static final String RUN_READINESS_AUTH = "RUN_READINESS_AUTH";
}
