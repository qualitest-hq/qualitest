package com.qualitest.web.mcp;

import com.qualitest.ai.mcp.protocol.McpJsonRpcDispatcher;
import com.qualitest.ai.mcp.protocol.McpSessionRegistry;
import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.mcp.McpJsonRpc;
import com.qualitest.project.domain.TestProjectUserSetting;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测 McpStreamableHttpController：Streamable HTTP 委托 Dispatcher 与会话头。
 * 边界：Dispatcher/SessionRegistry Mock；项目上下文来自请求属性。
 * 单跑：mvn test -DskipTests=false -pl qualitest-admin -am -Dtest=McpStreamableHttpControllerTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpStreamableHttpControllerTest {

    private static final long TEST_PROJECT_ID = 99L;
    private static final long OPERATOR_USER_ID = 1L;

    @Mock
    private McpJsonRpcDispatcher jsonRpcDispatcher;
    @Mock
    private McpSessionRegistry sessionRegistry;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        McpStreamableHttpController controller =
                new McpStreamableHttpController(jsonRpcDispatcher, sessionRegistry);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static TestProjectUserSetting projectSetting() {
        TestProjectUserSetting setting = new TestProjectUserSetting();
        setting.setTestProjectId(TEST_PROJECT_ID);
        setting.setUserId(OPERATOR_USER_ID);
        return setting;
    }

    /**
     * 前提：POST initialize；Dispatcher 返回带 session-abc 的响应。
     * 期望：HTTP 200；SESSION_HEADER=session-abc；jsonrpc=2.0；dispatch 带操作者 userId。
     */
    @Test
    @Order(1)
    @DisplayName("initialize 返回 JSON 并带会话头")
    void handlePost_initialize_returnsJsonWithSessionHeader() throws Exception {
        String responseBody = """
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}
                """;
        when(jsonRpcDispatcher.dispatch(any(), eq(TEST_PROJECT_ID), eq(OPERATOR_USER_ID)))
                .thenReturn(McpJsonRpcDispatcher.DispatchResult.withSession(responseBody, "session-abc"));

        mockMvc().perform(post(ProjectConstants.MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(ProjectConstants.PROJECT_SETTING_ATTR, projectSetting())
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(header().string(McpJsonRpc.SESSION_HEADER, "session-abc"))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"));
    }

    /**
     * 前提：POST tools/list；Dispatcher 返回空 tools；项目上下文 testProjectId=99。
     * 期望：dispatch 入参为项目 id 与操作者；HTTP 200；result.tools 为数组。
     */
    @Test
    @Order(2)
    @DisplayName("tools/list 委托 Dispatcher 并返回 tools 数组")
    void handlePost_toolsList_delegatesToDispatcher() throws Exception {
        String responseBody = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}
                """;
        when(jsonRpcDispatcher.dispatch(any(), eq(TEST_PROJECT_ID), eq(OPERATOR_USER_ID)))
                .thenReturn(McpJsonRpcDispatcher.DispatchResult.response(responseBody));

        mockMvc().perform(post(ProjectConstants.MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(ProjectConstants.PROJECT_SETTING_ATTR, projectSetting())
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }
}
