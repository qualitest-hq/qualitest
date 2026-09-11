package com.qualitest.ai.tools;

import java.util.Arrays;

/**
 * 测试流 AI 设计工具名注册表。
 * <p>
 * 每个工具两个开关：webAgent（Web 造流助手是否可见）、mcpAllowed（MCP 是否可调用）。
 * submit_* 一律仅 Web：改图画布必须走面板 Staging 确认，MCP 只读勘察。
 * 启动时要求：本枚举工具名、执行器已注册名、工具 JSON 定义名三者集合相同。
 */
public enum FlowDesignToolNames {

    SEARCH_APIS("search_apis", true, true),
    GET_API_DETAILS("get_api_details", true, true),
    GET_GRAPH_SUMMARY("get_graph_summary", true, true),
    GET_FLOW_META("get_flow_meta", true, true),
    LIST_PROJECT_ENVS("list_project_envs", true, true),
    LIST_ASSET_VARIABLES("list_asset_variables", true, true),
    UPSERT_ASSET_VARIABLES("upsert_asset_variables", true, false),
    APPEND_API_DESIGN_HINTS("append_api_design_hints", true, false),
    GET_NODE_DETAIL("get_node_detail", true, true),
    GET_EDGE_DETAIL("get_edge_detail", true, true),
    GET_SCENARIO_DETAIL("get_scenario_detail", true, true),
    GET_RUN_FAILURE("get_run_failure", true, true),
    GET_FLOW_API_HEALTH("get_flow_api_health", true, true),
    LIST_SUBFLOW_TEMPLATES("list_subflow_templates", true, true),
    GET_SUBFLOW_DETAIL("get_subflow_detail", true, true),
    /** 仅 MCP：按项目列测试流 */
    LIST_FLOWS("list_flows", false, true),
    /** 仅 MCP：读完整流与 graphJson */
    GET_FLOW("get_flow", false, true),

    // —— 分类型画布提交（每次恰好 1 个 Staging 单元；仅 Web）——
    SUBMIT_ADD_HTTP_NODE("submit_add_http_node", true, false),
    SUBMIT_ADD_ASSERT_NODE("submit_add_assert_node", true, false),
    SUBMIT_ADD_CONDITION_NODE("submit_add_condition_node", true, false),
    SUBMIT_ADD_ASSIGN_NODE("submit_add_assign_node", true, false),
    SUBMIT_ADD_DELAY_NODE("submit_add_delay_node", true, false),
    SUBMIT_ADD_SCRIPT_NODE("submit_add_script_node", true, false),
    SUBMIT_ADD_SUBFLOW_NODE("submit_add_subflow_node", true, false),

    SUBMIT_UPDATE_HTTP_NODE("submit_update_http_node", true, false),
    SUBMIT_UPDATE_ASSERT_NODE("submit_update_assert_node", true, false),
    SUBMIT_UPDATE_CONDITION_NODE("submit_update_condition_node", true, false),
    SUBMIT_UPDATE_ASSIGN_NODE("submit_update_assign_node", true, false),
    SUBMIT_UPDATE_DELAY_NODE("submit_update_delay_node", true, false),
    SUBMIT_UPDATE_SCRIPT_NODE("submit_update_script_node", true, false),
    SUBMIT_UPDATE_SUBFLOW_NODE("submit_update_subflow_node", true, false),

    SUBMIT_ADD_EDGE("submit_add_edge", true, false),
    SUBMIT_UPDATE_EDGE("submit_update_edge", true, false),

    SUBMIT_DELETE_NODE("submit_delete_node", true, false),
    SUBMIT_DELETE_EDGE("submit_delete_edge", true, false),
    SUBMIT_DELETE_SCENARIO("submit_delete_scenario", true, false),

    SUBMIT_ADD_SCENARIO("submit_add_scenario", true, false),
    SUBMIT_UPDATE_SCENARIO("submit_update_scenario", true, false);

    private final String id;
    private final boolean webAgent;
    private final boolean mcpAllowed;

    FlowDesignToolNames(String id, boolean webAgent, boolean mcpAllowed) {
        this.id = id;
        this.webAgent = webAgent;
        this.mcpAllowed = mcpAllowed;
    }

    /** OpenAI function 名 / 执行器路由键 */
    public String getId() {
        return id;
    }

    /** 是否注入 Web 造流 Agent 的 tools 列表 */
    public boolean isWebAgent() {
        return webAgent;
    }

    /** 是否允许经 MCP tools/call 调用 */
    public boolean isMcpAllowed() {
        return mcpAllowed;
    }

    /**
     * 是否为画布单元提交类工具。
     * 判定：名称以 submit_ 开头（含加/改/删节点边场景）。
     */
    public static boolean isSubmitUnitTool(String name) {
        return name != null && name.startsWith("submit_");
    }

    /** 按工具名查是否允许 MCP */
    public static boolean isMcpAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.mcpAllowed);
    }

    /** 按工具名查是否属于 Web Agent */
    public static boolean isWebAgent(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.webAgent);
    }

    /** Web Agent 应加载的全部工具 id */
    public static java.util.Set<String> webAgentToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isWebAgent)
                .map(FlowDesignToolNames::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** MCP 应暴露的全部工具 id */
    public static java.util.Set<String> mcpAllowedToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isMcpAllowed)
                .map(FlowDesignToolNames::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 按 id 反查枚举；未知返回 null */
    public static FlowDesignToolNames fromId(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.id.equals(name))
                .findFirst()
                .orElse(null);
    }
}
