package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.McpProjectGates;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 测工具定义加载：Web 半自动/全自动清单、MCP 只读清单、MCP 自动写追加写工具。
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignToolsDefinitionServiceTest {

    @Mock
    private FlowDesignToolExecutor flowDesignToolExecutor;

    private FlowDesignToolsDefinitionService service;

    @BeforeEach
    void setUp() {
        when(flowDesignToolExecutor.registeredToolNames()).thenReturn(FlowDesignToolExecutor.allDeclaredToolNames());
        service = new FlowDesignToolsDefinitionService(flowDesignToolExecutor);
        service.validateToolRegistryConsistency();
    }

    /**
     * 前提：执行器已注册全部声明工具；加载 Web JSON 清单。
     * 期望：数量与 webAgent 枚举一致；含 submit、upsert、run；不含 list_flows、get_flow。
     */
    @Test
    @Order(1)
    @DisplayName("Web JSON 含 submit/upsert/run，不含 list_flows")
    void loadToolsDefinition_containsSubmit_notListFlows() {
        List<String> names = service.loadToolsDefinition().stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();

        assertEquals(FlowDesignToolNames.webAgentToolIds().size(), names.size());
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_EDGE_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_SCENARIO_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertFalse(names.contains("commit_design_patch"));
    }

    /**
     * 前提：加载 Web 工具且 autopilotEnabled=false。
     * 期望：不含 run。
     */
    @Test
    @Order(2)
    @DisplayName("半自动时剔除 run")
    void loadToolsDefinition_withoutAutopilot_excludesRun() {
        List<String> names = service.loadToolsDefinition(false).stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
    }

    /**
     * 前提：加载 Web 工具且 autopilotEnabled=true。
     * 期望：含 run。
     */
    @Test
    @Order(3)
    @DisplayName("全自动注入 run")
    void loadToolsDefinition_withAutopilot_includesRun() {
        List<String> names = service.loadToolsDefinition(true).stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
    }

    /**
     * 前提：执行器已注册全部声明工具；加载 MCP 工具定义。
     * 期望：含 list_flows、get_flow、get_edge_detail、get_scenario_detail；不含任何 submit_*、upsert、append、run。
     */
    @Test
    @Order(4)
    @DisplayName("MCP 只读工具不含 submit/upsert/run")
    void loadMcpProtocolTools_excludesSubmit() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();

        assertEquals(FlowDesignToolNames.mcpAllowedToolIds().size(), names.size());
        assertFalse(names.stream().anyMatch(FlowDesignToolNames::isSubmitUnitTool));
        assertFalse(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertFalse(names.contains(FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertFalse(names.contains("commit_design_patch"));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_MCP_GUIDE_VERSION.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_EDGE_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_SCENARIO_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
    }

    /**
     * 前提：已加载 MCP 工具定义。
     * 期望：名称集合等于枚举中 mcpAllowed=true 的全部 id。
     */
    @Test
    @Order(5)
    @DisplayName("MCP 工具 id 等于枚举 mcpAllowed 集合")
    void mcpToolIds_matchEnum() {
        Set<String> mcpNames = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());

        assertEquals(FlowDesignToolNames.mcpAllowedToolIds(), mcpNames);
    }

    /**
     * 前提：开启 MCP 自动写流、未开跑流时的工具列表。
     * 期望：含只读工具与写工具，不含 run_test_flow；名称无重复。
     */
    @Test
    @Order(6)
    @DisplayName("MCP 写流列表含写工具不含跑流")
    void loadMcpProtocolTools_autopilot_includesWriteTools() {
        List<String> names = service.loadMcpProtocolTools(true, false, false).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        Set<String> unique = Set.copyOf(names);
        assertEquals(names.size(), unique.size());
        assertEquals(FlowDesignToolNames.mcpAutoWriteEnabledToolIds(), unique);
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
    }

    /**
     * 前提：写流与跑流都开。
     * 期望：列表含 run_test_flow；写流开时含 upsert_project_env。
     */
    @Test
    @Order(61)
    @DisplayName("MCP 写流+跑流列表含 run_test_flow；写流含 upsert_project_env")
    void loadMcpProtocolTools_autorun_includesRun() {
        List<String> names = service.loadMcpProtocolTools(true, true, false).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_PROJECT_ENV.getId()));
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
    }

    /**
     * 前提：未开全自动时的 MCP 只读列表。
     * 期望：不含 create_flow / update_flow_meta。
     */
    @Test
    @Order(7)
    @DisplayName("只读 MCP 列表不含 create_flow / update_flow_meta")
    void loadMcpProtocolTools_readonly_excludesCreateFlow() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertFalse(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
    }

    /**
     * 前提：MCP 跑流列表中的 run_test_flow。
     * 期望：inputSchema 必填 testFlowId；描述写明无画布当前流与自动跑流开关。
     */
    @Test
    @Order(8)
    @DisplayName("MCP run_test_flow 合同必填 testFlowId")
    @SuppressWarnings("unchecked")
    void loadMcpProtocolTools_runTestFlow_requiresTestFlowId() {
        Map<String, Object> runTool = service.loadMcpProtocolTools(true, true, false).stream()
                .filter(t -> FlowDesignToolNames.RUN_TEST_FLOW.getId().equals(String.valueOf(t.get("name"))))
                .findFirst()
                .orElseThrow();
        String desc = String.valueOf(runTool.get("description"));
        assertFalse(desc.contains("触发当前测试流"));
        assertTrue(desc.contains("testFlowId"));
        assertTrue(desc.contains("无画布"));
        assertTrue(desc.contains("允许 MCP 自动跑流"));
        Map<String, Object> schema = (Map<String, Object>) runTool.get("inputSchema");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("testFlowId"));
    }

    /**
     * 前提：MCP 只读列表中的 get_run_failure。
     * 期望：inputSchema 必填 runId。
     */
    @Test
    @Order(9)
    @DisplayName("MCP get_run_failure 合同必填 runId")
    @SuppressWarnings("unchecked")
    void loadMcpProtocolTools_getRunFailure_requiresRunId() {
        Map<String, Object> tool = service.loadMcpProtocolTools().stream()
                .filter(t -> FlowDesignToolNames.GET_RUN_FAILURE.getId().equals(String.valueOf(t.get("name"))))
                .findFirst()
                .orElseThrow();
        Map<String, Object> schema = (Map<String, Object>) tool.get("inputSchema");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("runId"));
    }

    /**
     * 前提：协议工具列表已加载 get_run_report。
     * 期望：inputSchema 必填 runId，properties 含可选 nodeId。
     */
    @Test
    @Order(10)
    @DisplayName("MCP get_run_report 合同必填 runId 且含 nodeId")
    @SuppressWarnings("unchecked")
    void loadMcpProtocolTools_getRunReport_requiresRunId() {
        Map<String, Object> tool = service.loadMcpProtocolTools().stream()
                .filter(t -> FlowDesignToolNames.GET_RUN_REPORT.getId().equals(String.valueOf(t.get("name"))))
                .findFirst()
                .orElseThrow();
        Map<String, Object> schema = (Map<String, Object>) tool.get("inputSchema");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("runId"));
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("nodeId"));
    }

    /**
     * 前提：写流与跑流都开。
     * 期望：回合工具表含 submit 与 run_test_flow，不含列流、建流、导入接口、规程版本。
     */
    @Test
    @Order(11)
    @DisplayName("回合工具表按写流跑流裁剪且不含协议专有工具")
    void loadTurnSessionTools_canvasCatalogWithoutProtocolExtras() {
        Set<String> names = service.loadTurnSessionTools(new McpProjectGates(true, true, true, true)).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());
        assertTrue(names.contains("submit_http_node"));
        assertTrue(names.contains("run_test_flow"));
        assertTrue(names.contains("search_apis"));
        assertFalse(names.contains("list_flows"));
        assertFalse(names.contains("create_flow"));
        assertFalse(names.contains("get_flow"));
        assertFalse(names.contains("import_apis"));
        assertFalse(names.contains("get_mcp_guide_version"));

        Set<String> readonly = service.loadTurnSessionTools(new McpProjectGates(true, false, false, true)).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());
        assertTrue(readonly.contains("search_apis"));
        assertFalse(readonly.contains("submit_http_node"));
        assertFalse(readonly.contains("run_test_flow"));
    }

    /**
     * 前提：Web 与协议两份工具定义文件均存在。
     * 期望：交集工具的 parameters 深度一致；跑流与读失败现场允许参数形状不同；
     * HTTP 节点 data 含 alwaysRun、authProfileId 与 extracts.entryKey。
     */
    @Test
    @Order(11)
    @DisplayName("Web/MCP 交集工具 parameters 对齐（除有意分轨）")
    void intersectionToolParameters_alignedExceptAllowlist() throws Exception {
        Map<String, JSONObject> web = loadToolParameters(FlowDesignPromptResources.TOOLS_DEFINITION);
        Map<String, JSONObject> mcp = loadToolParameters(FlowDesignPromptResources.MCP_PROTOCOL_TOOLS);
        Set<String> common = web.keySet().stream().filter(mcp::containsKey).collect(Collectors.toSet());
        assertFalse(common.isEmpty());

        // 协议侧强制传流 id / 运行 id，Web 侧可回退当前画布或上下文，参数形状允许不同
        Set<String> allowDifferentParams = Set.of(
                FlowDesignToolNames.RUN_TEST_FLOW.getId(),
                FlowDesignToolNames.GET_RUN_FAILURE.getId());

        List<String> drifts = new ArrayList<>();
        for (String name : common) {
            if (allowDifferentParams.contains(name)) {
                continue;
            }
            String webParams = JSON.toJSONString(web.get(name));
            String mcpParams = JSON.toJSONString(mcp.get(name));
            if (!webParams.equals(mcpParams)) {
                drifts.add(name);
            }
        }
        assertTrue(drifts.isEmpty(), "parameters 漂移: " + drifts);

        assertTrue(common.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
        JSONObject httpData = web.get(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId())
                .getJSONObject("properties")
                .getJSONObject("data")
                .getJSONObject("properties");
        assertTrue(httpData.containsKey("alwaysRun"));
        assertTrue(httpData.containsKey("authProfileId"));
        assertTrue(httpData.getJSONObject("extracts")
                .getJSONObject("items")
                .getJSONObject("properties")
                .containsKey("entryKey"));
    }

    /**
     * 从 classpath 工具定义 JSON 中按工具名取出 parameters 对象。
     *
     * @param resourcePath 资源路径
     * @return 工具名到 parameters 的映射
     */
    private static Map<String, JSONObject> loadToolParameters(String resourcePath) throws Exception {
        JSONArray arr = JSON.parseArray(FlowDesignPromptResources.loadText(resourcePath));
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject item = arr.getJSONObject(i);
            JSONObject fn = item.getJSONObject("function");
            if (fn == null) {
                continue;
            }
            String name = fn.getString("name");
            JSONObject parameters = fn.getJSONObject("parameters");
            if (name != null && parameters != null) {
                out.put(name, parameters);
            }
        }
        return out;
    }
}
