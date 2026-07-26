package com.qualitest.framework.security.filter;

import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.mcp.McpJsonRpc;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.service.ITestProjectUserSettingService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 项目开放 API（{@code /api/project/*}）的 Project Token 鉴权过滤器。
 * <p>
 * 从请求头 {@link ProjectConstants#PROJECT_TOKEN_HEADER} 读取 Token，校验通过后把
 * {@link TestProjectUserSetting} 写入请求属性 {@link ProjectConstants#PROJECT_SETTING_ATTR}，
 * 供 {@link com.qualitest.common.core.controller.ProjectController} 及 MCP 入口读取项目 id。
 * <p>
 * 鉴权失败时 HTTP 状态码均为 401，响应体格式按路径区分：
 * <ul>
 *   <li>{@link ProjectConstants#MCP_ENDPOINT}：JSON-RPC error，便于 IDE 客户端解析</li>
 *   <li>其他 {@code /api/project/} 路径：{@code {code,msg}} 业务 JSON</li>
 * </ul>
 *
 * @author qualitest
 * @date 2026-02-10
 */
@Component
public class ProjectTokenFilter extends OncePerRequestFilter {

    @Resource
    private ITestProjectUserSettingService projectUserSettingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        String requestPath = normalizePath(request.getRequestURI());

        if (!requestPath.startsWith("/api/project/")) {
            chain.doFilter(request, response);
            return;
        }

        String projectToken = request.getHeader(ProjectConstants.PROJECT_TOKEN_HEADER);
        if (StringUtils.isEmpty(projectToken)) {
            writeUnauthorized(response, requestPath, "缺少 Project Token");
            return;
        }

        try {
            TestProjectUserSetting setting = projectUserSettingService.validateProjectToken(projectToken);
            request.setAttribute(ProjectConstants.PROJECT_SETTING_ATTR, setting);
            chain.doFilter(request, response);

        } catch (ServiceException e) {
            writeUnauthorized(response, requestPath, e.getMessage());
        }
    }

    /**
     * 写 401 响应；MCP 主端点使用 JSON-RPC 信封，其余项目 API 使用 {@code {code,msg}} JSON。
     */
    private void writeUnauthorized(HttpServletResponse response, String requestPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        if (isMcpEndpoint(requestPath)) {
            response.getWriter().write(McpJsonRpc.authErrorJson(message));
        } else {
            response.getWriter().write(buildRestAuthErrorJson(message));
        }
    }

    private static boolean isMcpEndpoint(String requestPath) {
        return ProjectConstants.MCP_ENDPOINT.equals(requestPath);
    }

    private static String buildRestAuthErrorJson(String message) {
        return String.format("{\"code\":401,\"msg\":\"%s\"}", McpJsonRpc.escapeJson(message));
    }

    /** 去掉查询串，得到用于路径匹配的 URI 路径部分。 */
    private static String normalizePath(String requestUri) {
        if (requestUri == null) {
            return "";
        }
        int queryIdx = requestUri.indexOf('?');
        return queryIdx >= 0 ? requestUri.substring(0, queryIdx) : requestUri;
    }
}
