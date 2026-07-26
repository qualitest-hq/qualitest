package com.qualitest.web.mcp;

import com.qualitest.ai.mcp.protocol.McpJsonRpcDispatcher;
import com.qualitest.ai.mcp.protocol.McpSessionRegistry;
import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.mcp.McpJsonRpc;
import com.qualitest.project.domain.TestProjectUserSetting;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.qualitest.common.test.FlowTestSections.begin;
import static com.qualitest.common.test.FlowTestSections.end;
import static com.qualitest.common.test.FlowTestSections.log;
import static com.qualitest.common.test.FlowTestSections.quote;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link McpStreamableHttpController} 单元测试。
 * <p>
 * 验证 Streamable HTTP 适配层：POST 委托 {@link McpJsonRpcDispatcher}、
 * initialize 响应写入 {@link McpJsonRpc#SESSION_HEADER}、项目上下文从请求属性读取 testProjectId。
 * Dispatcher 与 SessionRegistry 使用 Mock。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-admin -am -DskipTests=false -Dtest=McpStreamableHttpControllerTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpStreamableHttpControllerTest {

    private static final long TEST_PROJECT_ID = 99L;

    @Mock
    private McpJsonRpcDispatcher jsonRpcDispatcher;
    @Mock
    private McpSessionRegistry sessionRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        McpStreamableHttpController controller =
                new McpStreamableHttpController(jsonRpcDispatcher, sessionRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers()
                .build();
        bindProjectSetting(TEST_PROJECT_ID);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * POST initialize，Dispatcher 返回带 session 的 JSON-RPC 响应。
     * 期望：HTTP 200；响应头 {@link McpJsonRpc#SESSION_HEADER}=session-abc；响应体 jsonrpc=2.0。
     */
    @Test
    @Order(1)
    void handlePost_initialize_returnsJsonWithSessionHeader() throws Exception {
        begin("handlePost_initialize_returnsJsonWithSessionHeader");
        String responseBody = """
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}
                """;
        when(jsonRpcDispatcher.dispatch(any(), eq(TEST_PROJECT_ID)))
                .thenReturn(McpJsonRpcDispatcher.DispatchResult.withSession(responseBody, "session-abc"));

        mockMvc.perform(post(ProjectConstants.MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(header().string(McpJsonRpc.SESSION_HEADER, "session-abc"))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"));
        log("sessionHeader=" + quote("session-abc") + " status=200");
        end("handlePost_initialize_returnsJsonWithSessionHeader");
    }

    /**
     * POST tools/list，Dispatcher 返回空工具列表。
     * 期望：dispatch 入参 testProjectId=99；HTTP 200；result.tools 为数组。
     */
    @Test
    @Order(2)
    void handlePost_toolsList_delegatesToDispatcher() throws Exception {
        begin("handlePost_toolsList_delegatesToDispatcher");
        String responseBody = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}
                """;
        when(jsonRpcDispatcher.dispatch(any(), eq(TEST_PROJECT_ID)))
                .thenReturn(McpJsonRpcDispatcher.DispatchResult.response(responseBody));

        mockMvc.perform(post(ProjectConstants.MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
        log("testProjectId=99 tools=array");
        end("handlePost_toolsList_delegatesToDispatcher");
    }

    private static void bindProjectSetting(long testProjectId) {
        TestProjectUserSetting setting = new TestProjectUserSetting();
        setting.setTestProjectId(testProjectId);
        setting.setUserId(1L);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getAttribute(ProjectConstants.PROJECT_SETTING_ATTR))
                .thenReturn(setting);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
