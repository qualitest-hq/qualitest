package com.qualitest.framework.security.filter;

import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.service.ITestProjectUserSettingService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.qualitest.common.test.FlowTestSections.begin;
import static com.qualitest.common.test.FlowTestSections.end;
import static com.qualitest.common.test.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectTokenFilter} 单元测试。
 * <p>
 * 验证项目 Token 鉴权：MCP 端点失败时返回 JSON-RPC 错误体（-32001），
 * 普通 REST 项目接口失败时返回 {@code {code,msg}}；合法 Token 放行 FilterChain。
 * SettingService 使用 Mock。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-framework -am -DskipTests=false -Dtest=ProjectTokenFilterTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTokenFilterTest {

    @Mock
    private ITestProjectUserSettingService projectUserSettingService;
    @Mock
    private FilterChain filterChain;

    private ProjectTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ProjectTokenFilter();
        org.springframework.test.util.ReflectionTestUtils
                .setField(filter, "projectUserSettingService", projectUserSettingService);
    }

    /**
     * POST {@link ProjectConstants#MCP_ENDPOINT}，未携带 X-Project-Token。
     * 期望：HTTP 401；响应体含 jsonrpc=2.0 与 error.code=-32001；不进入 FilterChain。
     */
    @Test
    @Order(1)
    void mcpEndpoint_missingToken_returnsJsonRpcError() throws Exception {
        begin("mcpEndpoint_missingToken_returnsJsonRpcError");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ProjectConstants.MCP_ENDPOINT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"jsonrpc\":\"2.0\""));
        assertTrue(response.getContentAsString().contains("\"code\":-32001"));
        verify(filterChain, never()).doFilter(request, response);
        log("status=401 errorCode=-32001 chainBlocked=true");
        end("mcpEndpoint_missingToken_returnsJsonRpcError");
    }

    /**
     * POST MCP 端点，Token 校验失败（ServiceException: Token 无效）。
     * 期望：HTTP 401；响应体为 JSON-RPC 错误且含业务消息「Token 无效」。
     */
    @Test
    @Order(2)
    void mcpEndpoint_invalidToken_returnsJsonRpcError() throws Exception {
        begin("mcpEndpoint_invalidToken_returnsJsonRpcError");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ProjectConstants.MCP_ENDPOINT);
        request.addHeader(ProjectConstants.PROJECT_TOKEN_HEADER, "bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(projectUserSettingService.validateProjectToken("bad-token"))
                .thenThrow(new ServiceException("Token 无效"));

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token 无效"));
        assertTrue(response.getContentAsString().contains("\"jsonrpc\":\"2.0\""));
        log("status=401 messageContains=Token无效");
        end("mcpEndpoint_invalidToken_returnsJsonRpcError");
    }

    /**
     * GET 普通项目 REST 接口 /api/project/apis，未携带 Token。
     * 期望：HTTP 401；响应体为传统 {@code {code:401,msg:...}} 结构，非 JSON-RPC。
     */
    @Test
    @Order(3)
    void restProjectEndpoint_missingToken_returnsRestError() throws Exception {
        begin("restProjectEndpoint_missingToken_returnsRestError");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/project/apis");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
        assertTrue(response.getContentAsString().contains("\"msg\""));
        log("status=401 restFormat=true");
        end("restProjectEndpoint_missingToken_returnsRestError");
    }

    /**
     * POST MCP 端点，Token 校验通过。
     * 期望：进入 FilterChain；{@link TestProjectUserSetting} 写入请求属性 PROJECT_SETTING_ATTR。
     */
    @Test
    @Order(4)
    void mcpEndpoint_validToken_continuesChain() throws Exception {
        begin("mcpEndpoint_validToken_continuesChain");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ProjectConstants.MCP_ENDPOINT);
        request.addHeader(ProjectConstants.PROJECT_TOKEN_HEADER, "good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TestProjectUserSetting setting = new TestProjectUserSetting();
        setting.setTestProjectId(1L);
        when(projectUserSettingService.validateProjectToken("good-token")).thenReturn(setting);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(setting, request.getAttribute(ProjectConstants.PROJECT_SETTING_ATTR));
        log("testProjectId=1 chainContinued=true");
        end("mcpEndpoint_validToken_continuesChain");
    }
}
