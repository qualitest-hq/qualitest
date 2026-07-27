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
 * 测 ProjectTokenFilter：MCP/REST 项目 Token 鉴权与错误体格式。
 * 边界：SettingService Mock；MCP 用 JSON-RPC -32001，REST 用 {code,msg}。
 * 单跑：mvn test -DskipTests=false -pl qualitest-framework -am -Dtest=ProjectTokenFilterTest
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
     * 前提：POST MCP 端点，未带 X-Project-Token。
     * 期望：401；JSON-RPC error.code=-32001；不进入 FilterChain。
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
     * 前提：POST MCP；Token 校验抛「Token 无效」。
     * 期望：401；JSON-RPC 错误体含该消息。
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
     * 前提：GET /api/project/apis，未带 Token。
     * 期望：401；响应为 {code:401,msg:...}，非 JSON-RPC。
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
     * 前提：POST MCP；Token 校验通过。
     * 期望：进入 FilterChain；PROJECT_SETTING_ATTR 写入请求属性。
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
