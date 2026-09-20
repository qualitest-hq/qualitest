package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.McpPromptResourceService;
import com.qualitest.ai.mcp.McpToolInvokeService;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 MCP JSON-RPC 分发：握手、列工具、调工具、错误与通知。
 * 边界：Dispatcher 协作对象全部 Mock；不启 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpJsonRpcDispatcherTest
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
    private McpPromptResourceService mcpPromptResourceService;
    private McpJsonRpcDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        sessionRegistry = new McpSessionRegistry();
        mcpPromptResourceService = new McpPromptResourceService();
        dispatcher = new McpJsonRpcDispatcher(
                toolsDefinitionService,
                mcpToolInvokeService,
                argumentsMapper,
                sessionRegistry,
                mcpPromptResourceService);
    }

    /**
     * 前提：POST initialize，testProjectId=42，项目未开 MCP 自动写流与导入接口。
     * 期望：成功响应；sessionId 非空；serverInfo 含 testProjectId=42、version 无门控后缀、guideVersion 非空；
     * capabilities 含 tools、prompts、resources；tools 对象存在且不含 listChanged
     * （表示不主动推送工具列表变更，改开关须客户端重连）。
     */
    @Test
    @Order(1)
    @DisplayName("initialize 返回 serverInfo 与 session")
    void dispatch_initialize_returnsServerInfoAndSession() {
        when(mcpToolInvokeService.resolveMcpGates(42L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, false));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 42L);

        assertNotNull(result.getSessionId());
        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals("2.0", json.getString("jsonrpc"));
        assertEquals(1, json.getIntValue("id"));
        JSONObject serverInfo = json.getJSONObject("result").getJSONObject("serverInfo");
        assertEquals("42", serverInfo.getString("testProjectId"));
        assertEquals("1.0.0", serverInfo.getString("version"));
        assertEquals(mcpPromptResourceService.guideVersion(false, false, false),
                serverInfo.getString("guideVersion"));
        assertFalse(serverInfo.getString("guideVersion").contains("+"));
        JSONObject capabilities = json.getJSONObject("result").getJSONObject("capabilities");
        assertTrue(capabilities.containsKey("tools"));
        assertFalse(capabilities.getJSONObject("tools").containsKey("listChanged"));
        assertTrue(capabilities.containsKey("prompts"));
        assertTrue(capabilities.containsKey("resources"));
    }

    /**
     * 前提：POST initialize，项目已开 MCP 自动写流。
     * 期望：serverInfo.version 带 +autowrite；mcpAutoWriteEnabled=true。
     */
    @Test
    @Order(2)
    @DisplayName("initialize 全自动时 version 带 autopilot 后缀")
    void dispatch_initialize_autopilot_versionsServerInfo() {
        when(mcpToolInvokeService.resolveMcpGates(42L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(true, false, false));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 42L);

        JSONObject serverInfo = JSON.parseObject(result.getResponseBody())
                .getJSONObject("result").getJSONObject("serverInfo");
        assertEquals("1.0.0+autowrite", serverInfo.getString("version"));
        assertEquals(mcpPromptResourceService.guideVersion(true, false, false),
                serverInfo.getString("guideVersion"));
        assertTrue(serverInfo.getString("guideVersion").endsWith("+autowrite"));
        assertTrue(serverInfo.getBooleanValue("mcpAutoWriteEnabled"));
        assertTrue(!serverInfo.getBooleanValue("mcpImportApisEnabled"));
    }

    /**
     * 前提：POST initialize，仅开导入接口。
     * 期望：version 带 +importApis；guideVersion 带同款后缀；mcpImportApisEnabled=true。
     */
    @Test
    @Order(21)
    @DisplayName("initialize 导入开时 version 带 importApis 后缀")
    void dispatch_initialize_importApis_versionsServerInfo() {
        when(mcpToolInvokeService.resolveMcpGates(42L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, true));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 42L);

        JSONObject serverInfo = JSON.parseObject(result.getResponseBody())
                .getJSONObject("result").getJSONObject("serverInfo");
        assertEquals("1.0.0+importApis", serverInfo.getString("version"));
        assertEquals(mcpPromptResourceService.guideVersion(false, false, true),
                serverInfo.getString("guideVersion"));
        assertTrue(serverInfo.getString("guideVersion").endsWith("+importApis"));
        assertTrue(serverInfo.getBooleanValue("mcpImportApisEnabled"));
    }

    /**
     * 前提：tools/list；项目未开全自动；Mock 返回 1 个只读工具。
     * 期望：响应 tools 数组长度为 1。
     */
    @Test
    @Order(3)
    @DisplayName("tools/list 返回协议工具列表")
    void dispatch_toolsList_returnsProtocolTools() {
        List<Map<String, Object>> mcpTools = List.of(Map.of("name", "list_flows"));
        when(mcpToolInvokeService.resolveMcpGates(1L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, false));
        when(toolsDefinitionService.loadMcpProtocolTools(any(McpToolInvokeService.McpProjectGates.class)))
                .thenReturn(mcpTools);

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals(1, json.getJSONObject("result").getJSONArray("tools").size());
    }

    /**
     * 前提：tools/call search_apis，arguments.keyword=login。
     * 期望：委托 invoke；content[0].text 为工具返回 JSON。
     */
    @Test
    @Order(4)
    @DisplayName("tools/call 委托 invoke 并回写 content")
    void dispatch_toolsCall_invokesService() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("keyword", "login"));
        when(argumentsMapper.fromToolArguments(any())).thenReturn(params);
        when(mcpToolInvokeService.invoke(eq("search_apis"), any(), eq(1L), isNull()))
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
        verify(mcpToolInvokeService).invoke(eq("search_apis"), any(), eq(1L), isNull());
    }

    /**
     * 前提：tools/call bad_tool；InvokeService 抛 ServiceException。
     * 期望：仍返回 JSON-RPC result；isError=true。
     */
    @Test
    @Order(5)
    @DisplayName("tools/call 异常时 isError=true")
    void dispatch_toolsCall_serviceException_returnsIsError() {
        when(argumentsMapper.fromToolArguments(any())).thenReturn(new McpToolInvokeParams());
        when(mcpToolInvokeService.invoke(eq("bad_tool"), any(), eq(1L), isNull()))
                .thenThrow(new ServiceException("MCP 不支持的工具: bad_tool"));

        String body = """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"bad_tool","arguments":{}}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertTrue(json.getJSONObject("result").getBooleanValue("isError"));
    }

    /**
     * 前提：method=unknown/method。
     * 期望：JSON-RPC error.code=-32601。
     */
    @Test
    @Order(6)
    @DisplayName("未知 method 返回 -32601")
    void dispatch_unknownMethod_returnsError() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"unknown/method\"}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals(-32601, json.getJSONObject("error").getIntValue("code"));
    }

    /**
     * 前提：notifications/initialized（无 id）。
     * 期望：notification=true；responseBody=null。
     */
    @Test
    @Order(7)
    @DisplayName("通知无 id 时返回空通知")
    void dispatch_notification_returnsEmptyNotification() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        assertTrue(result.isNotification());
        assertNull(result.getResponseBody());
    }

    /**
     * 前提：工具返回 JSON 含 error 字段。
     * 期望：result.isError=true。
     */
    @Test
    @Order(8)
    @DisplayName("业务 error 时 isError=true")
    void dispatch_toolsCall_businessError_returnsIsError() {
        when(argumentsMapper.fromToolArguments(any())).thenReturn(new McpToolInvokeParams());
        when(mcpToolInvokeService.invoke(eq("get_flow"), any(), eq(1L), isNull()))
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
    }

    /**
     * 前提：prompts/list。
     * 期望：含 qualitest_core / survey / fix_run / sync_local_skill 共 4 条。
     */
    @Test
    @Order(9)
    @DisplayName("prompts/list 返回四条规程 Prompt")
    void dispatch_promptsList_returnsFourPrompts() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"prompts/list\",\"params\":{}}";
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        var prompts = json.getJSONObject("result").getJSONArray("prompts");
        assertEquals(4, prompts.size());
        assertEquals(McpPromptResourceService.PROMPT_CORE, prompts.getJSONObject(0).getString("name"));
        assertEquals(McpPromptResourceService.PROMPT_SYNC_LOCAL_SKILL,
                prompts.getJSONObject(3).getString("name"));
    }

    /**
     * 前提：prompts/get qualitest_core；项目只读门控。
     * 期望：messages 含 tools/list 规矩；无 import_apis / 立即写库写流段。
     */
    @Test
    @Order(10)
    @DisplayName("prompts/get core 只读裁剪")
    void dispatch_promptsGet_core_returnsGuideBody() {
        when(mcpToolInvokeService.resolveMcpGates(1L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, false));
        String body = """
                {"jsonrpc":"2.0","id":9,"method":"prompts/get","params":{"name":"qualitest_core"}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        String text = json.getJSONObject("result").getJSONArray("messages")
                .getJSONObject(0).getJSONObject("content").getString("text");
        assertTrue(text.contains("guideVersion"));
        assertTrue(text.contains("tools/list"));
        assertFalse(text.contains("import_apis"));
        assertFalse(text.contains("已立即写库") || text.contains("立即写库"));
    }

    /**
     * 前提：prompts/get qualitest_core；写流与导入均开。
     * 期望：含立即写库与 import_apis。
     */
    @Test
    @Order(101)
    @DisplayName("prompts/get core 门控全开")
    void dispatch_promptsGet_core_bothGates() {
        when(mcpToolInvokeService.resolveMcpGates(1L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(true, false, true));
        String body = """
                {"jsonrpc":"2.0","id":91,"method":"prompts/get","params":{"name":"qualitest_core"}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);
        String text = JSON.parseObject(result.getResponseBody())
                .getJSONObject("result").getJSONArray("messages")
                .getJSONObject(0).getJSONObject("content").getString("text");
        assertTrue(text.contains("已立即写库") || text.contains("立即写库"));
        assertTrue(text.contains("import_apis"));
    }

    /**
     * 前提：prompts/get 未知名。
     * 期望：error.code=-32602。
     */
    @Test
    @Order(11)
    @DisplayName("prompts/get 未知名返回 -32602")
    void dispatch_promptsGet_unknown_returnsInvalidParams() {
        when(mcpToolInvokeService.resolveMcpGates(1L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, false));
        String body = """
                {"jsonrpc":"2.0","id":10,"method":"prompts/get","params":{"name":"no_such_prompt"}}
                """;
        McpJsonRpcDispatcher.DispatchResult result = dispatcher.dispatch(body, 1L);

        JSONObject json = JSON.parseObject(result.getResponseBody());
        assertEquals(-32602, json.getJSONObject("error").getIntValue("code"));
    }

    /**
     * 前提：resources/list 与 resources/read（只读门控）。
     * 期望：list 含 qualitest://docs/core；read 正文无写流段。
     */
    @Test
    @Order(12)
    @DisplayName("resources list/read 返回 CORE 规程")
    void dispatch_resources_listAndRead_returnCore() {
        when(mcpToolInvokeService.resolveMcpGates(1L))
                .thenReturn(new McpToolInvokeService.McpProjectGates(false, false, false));
        McpJsonRpcDispatcher.DispatchResult listResult = dispatcher.dispatch(
                "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"resources/list\",\"params\":{}}", 1L);
        JSONObject listJson = JSON.parseObject(listResult.getResponseBody());
        assertEquals(McpPromptResourceService.RESOURCE_CORE_URI,
                listJson.getJSONObject("result").getJSONArray("resources")
                        .getJSONObject(0).getString("uri"));

        String readBody = """
                {"jsonrpc":"2.0","id":12,"method":"resources/read","params":{"uri":"qualitest://docs/core"}}
                """;
        McpJsonRpcDispatcher.DispatchResult readResult = dispatcher.dispatch(readBody, 1L);
        String text = JSON.parseObject(readResult.getResponseBody())
                .getJSONObject("result").getJSONArray("contents")
                .getJSONObject(0).getString("text");
        assertTrue(text.contains("guideVersion"));
        assertFalse(text.contains("import_apis"));
        assertFalse(text.contains("run_test_flow"));
    }
}
