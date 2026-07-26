package com.qualitest.common.constant;

/**
 * 测试项目开放 API 相关常量。
 *
 * @author qualitest
 */
public class ProjectConstants {

    /** 项目 Token 请求头名，IDE 与开放 API 客户端须在请求中携带。 */
    public static final String PROJECT_TOKEN_HEADER = "X-Project-Token";

    /**
     * 过滤器校验 Token 后写入 {@link jakarta.servlet.http.HttpServletRequest} 的属性键，
     * 值为 {@link com.qualitest.project.domain.TestProjectUserSetting}。
     */
    public static final String PROJECT_SETTING_ATTR = "projectSetting";

    /**
     * 质衡 MCP Streamable HTTP 服务挂载路径。
     * POST 处理 JSON-RPC 上行；GET 在携带 {@code Mcp-Session-Id} 时建立 SSE 下行流。
     */
    public static final String MCP_ENDPOINT = "/api/project/mcp";

}
