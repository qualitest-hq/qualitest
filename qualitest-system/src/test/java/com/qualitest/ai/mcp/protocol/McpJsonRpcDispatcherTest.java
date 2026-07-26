package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.McpToolInvokeService;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.qualitest.common.test.FlowTestSections.begin;
import static com.qualitest.common.test.FlowTestSections.end;
import static com.qualitest.common.test.FlowTestSections.log;
import static com.qualitest.common.test.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link McpJsonRpcDispatcher} 单元测试。
 * <p>
 * 验证 JSON-RPC 分发：initialize 会话与 serverInfo、tools/list 与 tools/call 委托、
 * 未知方法与通知类请求的错误/空响应处理。依赖服务使用 Mock，不访问数据库。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=McpJsonRpcDispatcherTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpJsonRpcDispatcherTest {

    @Mock
    private FlowDesignToolsDefinitionService toolsDefinitionService;
    @Mock
    private McpToolInvokeService mcpToolInvokeService;
    @Mock
    private McpToolArgumentsMapper argumentsMapper;

    private McpSessionRegistry sessionRegistry;
    private McpJsonRpcDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        sessionRegistry = new McpSessionRegistry();
        dispatcher = new McpJsonRpcDispatcher(
                toolsDefinitionService,
                mcpToolInvokeService,
                argumentsMapper,
                sessionRegistry);
    }

    /**
     * POST initialize 请求体，testProjectId=42。
     * 期望：JSON-RPC 成功响应；DispatchResult.sessionId 非空；serverInfo.testProjectId 为 "42"。
     */
    @Test
    @Order(1)
    void dispatch_initialize_returnsServerInfoAndSession() {
        begin("dispatch_initialize_returnsServerInfoAndSession");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 42L);

        assertNotNull(result.getSessionId());
        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals("2.0", json.getString("jsonrpc"));
        assertEquals(1, json.getIntValue("id"));
        assertEquals("42", json.getJSONObject("result").getJSONObject("serverInfo").getString("testProjectId"));
        log("sessionId=" + quote(result.getSessionId())
                + " testProjectId=" + quote(json.getJSONObject("result").getJSONObject("serverInfo").getString("testProjectId")));
        end("dispatch_initialize_returnsServerInfoAndSession");
    }

    /**
     * tools/list 请求，Mock 返回 1 个协议格式工具定义。
     * 期望：委托 {@link FlowDesignToolsDefinitionService#loadMcpProtocolTools()}；result.tools 数组长度为 1。
     */
    @Test
    @Order(2)
    void dispatch_toolsList_returnsProtocolTools() {
        begin("dispatch_toolsList_returnsProtocolTools");
        List<Map<String, Object>> mcpTools = List.of(Map.of("name", "list_flows"));
        when(toolsDefinitionService.loadMcpProtocolTools()).thenReturn(mcpTools);

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals(1, json.getJSONObject("result").getJSONArray("tools").size());
        log("toolsCount=1");
        end("dispatch_toolsList_returnsProtocolTools");
    }

    /**
     * tools/call 调用 search_apis，arguments 含 keyword=login。
     * 期望：经 {@link McpToolArgumentsMapper} 映射后委托 {@link McpToolInvokeService#invoke}；content[0].text 为工具返回 JSON。
     */
    @Test
    @Order(3)
    void dispatch_toolsCall_invokesService() {
        begin("dispatch_toolsCall_invokesService");
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("keyword", "login"));
        when(argumentsMapper.fromToolArguments(any())).thenReturn(params);
        when(mcpToolInvokeService.invoke(eq("search_apis"), any(), eq(1L)))
                .thenReturn(McpToolResult.builder()
                        .tool("search_apis")
                        .resultJson("{\"apis\":[]}")
                        .build());

        String body = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_apis","arguments":{"keyword":"login"}}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals("{\"apis\":[]}", json.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text"));
        verify(mcpToolInvokeService).invoke(eq("search_apis"), any(), eq(1L));
        log("tool=search_apis contentText=" + quote("{\"apis\":[]}"));
        end("dispatch_toolsCall_invokesService");
    }

    /**
     * tools/call 调用 bad_tool，InvokeService 抛出 {@link ServiceException}。
     * 期望：仍返回 JSON-RPC result；result.isError=true。
     */
    @Test
    @Order(4)
    void dispatch_toolsCall_serviceException_returnsIsError() {
        begin("dispatch_toolsCall_serviceException_returnsIsError");
        when(argumentsMapper.fromToolArguments(any())).thenReturn(new McpToolInvokeParams());
        when(mcpToolInvokeService.invoke(eq("bad_tool"), any(), eq(1L)))
                .thenThrow(new ServiceException("MCP 不支持的工具: bad_tool"));

        String body = """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"bad_tool","arguments":{}}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertTrue(json.getJSONObject("result").getBooleanValue("isError"));
        log("isError=true");
        end("dispatch_toolsCall_serviceException_returnsIsError");
    }

    /**
     * tools/call 工具返回 JSON 含 error 字段时。
     * 期望：result.isError=true。
     */
    @Test
    @Order(7)
    void dispatch_toolsCall_businessError_returnsIsError() {
        begin("dispatch_toolsCall_businessError_returnsIsError");
        when(argumentsMapper.fromToolArguments(any())).thenReturn(new McpToolInvokeParams());
        when(mcpToolInvokeService.invoke(eq("get_flow"), any(), eq(1L)))
                .thenReturn(McpToolResult.builder()
                        .tool("get_flow")
                        .resultJson("{\"error\":\"测试流不存在\"}")
                        .error(true)
                        .build());

        String body = """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_flow","arguments":{}}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertTrue(json.getJSONObject("result").getBooleanValue("isError"));
        log("isError=true businessError");
        end("dispatch_toolsCall_businessError_returnsIsError");
    }

    /**
     * method 为未实现的 unknown/method。
     * 期望：JSON-RPC error.code=-32601（Method not found）。
     */
    @Test
    @Order(5)
    void dispatch_unknownMethod_returnsError() {
        begin("dispatch_unknownMethod_returnsError");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"unknown/method\"}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals(-32601, json.getJSONObject("error").getIntValue("code"));
        log("errorCode=-32601");
        end("dispatch_unknownMethod_returnsError");
    }

    /**
     * notifications/initialized 无 id 的通知请求。
     * 期望：DispatchResult.notification=true；responseBody 为 null。
     */
    @Test
    @Order(6)
    void dispatch_notification_returnsEmptyNotification() {
        begin("dispatch_notification_returnsEmptyNotification");
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        assertTrue(result.isNotification());
        assertNull(result.getResponseBody());
        log("notification=true responseBody=null");
        end("dispatch_notification_returnsEmptyNotification");
    }
}
