package com.qualitest.api.util;

/**
 * 单元测试用的 API 配置 JSON 样例（{@code configVersion: 1}）。
 */
public final class ApiConfigTestFixtures {

    public static final int CONFIG_VERSION = ApiConfigJsonSupport.CONFIG_VERSION;

    /** 最小 GET 请求：空参数数组，body.mode=none */
    public static final String MIN_REQUEST_CONFIG = """
            {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none","json":{"schema":null,"example":null}}}
            """;

    /** 最小响应：单条 object schema 的成功项 */
    public static final String MIN_RESPONSE_CONFIG = """
            {"configVersion":1,"responses":[{"id":"resp-test001","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object"},"example":null}]}
            """;

    /** POST + json body，example 为 {@code {}} */
    public static final String REQUEST_JSON_BODY = """
            {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":"{}"}}}
            """;

    /** GET + body.mode=none */
    public static final String REQUEST_NONE_BODY = """
            {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none","json":{"schema":null,"example":null}}}
            """;

    /** POST + 带必填 query 参数 page */
    public static final String REQUEST_POST_WITH_QUERY = """
            {"configVersion":1,"method":"POST","queryParams":[{"name":"page","required":true}],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none","json":{"schema":null,"example":null}}}
            """;

    /** 响应 schema 含 properties.code.integer */
    public static final String RESPONSE_WITH_CODE_SCHEMA = """
            {"configVersion":1,"responses":[{"id":"resp-test001","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"code":{"type":"integer"}}},"example":null}]}
            """;

    /** 登录接口请求体 example（含流程变量占位符） */
    public static final String LOGIN_REQUEST_CONFIG = """
            {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"schema":null,"example":"{\\"username\\":\\"{{flow.user}}\\",\\"password\\":\\"{{asset.pwd}}\\"}"}}}
            """;

    private ApiConfigTestFixtures() {
    }
}
