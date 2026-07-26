package com.qualitest.ai.tools;

import java.util.Arrays;

/**
 * 测试流 AI 设计工具名注册表（13 个工具）。
 * <p>
 * 每个工具有两个暴露开关：
 * <ul>
 *   <li>{@code webAgent} — 是否出现在 Web「AI 设计」助手的 function 列表</li>
 *   <li>{@code mcpAllowed} — 是否允许经 MCP tools/call 调用</li>
 * </ul>
 * Web 共 11 个（含 submit_flow_design_patch、get_flow_api_health）；
 * MCP 共 12 个只读（含 list_flows、get_flow，不含 submit）。
 * 启动时会核对枚举、执行器注册表与 JSON 定义中的工具名是否齐全且无多余项。
 */
public enum FlowDesignToolNames {

    SEARCH_APIS("search_apis", true, true),
    GET_API_DETAIL("get_api_detail", true, true),
    GET_GRAPH_SUMMARY("get_graph_summary", true, true),
    GET_FLOW_META("get_flow_meta", true, true),
    LIST_PROJECT_ENVS("list_project_envs", true, true),
    GET_NODE_DETAIL("get_node_detail", true, true),
    GET_RUN_FAILURE("get_run_failure", true, true),
    /**
     * 检查画布上「项目接口」HTTP 节点的 API 语义健康告警。
     * 覆盖：绑定接口是否还在、测值参数是否孤儿、抽取路径是否还能对上响应结构。
     * Web Agent 与 MCP 均可调用。
     */
    GET_FLOW_API_HEALTH("get_flow_api_health", true, true),
    /** 列举平台内置子流模板与项目内可引用的测试流摘要 */
    LIST_SUBFLOW_TEMPLATES("list_subflow_templates", true, true),
    /** 读取指定测试流的拓扑摘要与 flowOutputs 声明 */
    GET_SUBFLOW_DETAIL("get_subflow_detail", true, true),
    /** 按项目列举测试流（仅 MCP；不含 graphJson） */
    LIST_FLOWS("list_flows", false, true),
    /** 读取单条测试流含完整 graphJson（仅 MCP） */
    GET_FLOW("get_flow", false, true),
    SUBMIT_FLOW_DESIGN_PATCH("submit_flow_design_patch", true, false);

    private final String id;
    private final boolean webAgent;
    private final boolean mcpAllowed;

    FlowDesignToolNames(String id, boolean webAgent, boolean mcpAllowed) {
        this.id = id;
        this.webAgent = webAgent;
        this.mcpAllowed = mcpAllowed;
    }

    public String getId() {
        return id;
    }

    public boolean isWebAgent() {
        return webAgent;
    }

    public boolean isMcpAllowed() {
        return mcpAllowed;
    }

    public static boolean isMcpAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.mcpAllowed);
    }

    public static boolean isWebAgent(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.webAgent);
    }

    public static java.util.Set<String> webAgentToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isWebAgent)
                .map(FlowDesignToolNames::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public static java.util.Set<String> mcpAllowedToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isMcpAllowed)
                .map(FlowDesignToolNames::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

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
