package com.qualitest.ai.tools;

import java.util.Arrays;

/**
 * 测试流 AI 设计工具名注册表（共 16 个）。
 * <p>
 * 每个工具两个开关：webAgent（Web「AI 设计」助手是否可见）、mcpAllowed（MCP 是否可调用）。
 * Web 暴露 14 个（含画布提交、素材写入、设计提示 append）；MCP 暴露 13 个只读（含按项目列流、读流，不含写类工具）。
 * 启动时校验：本枚举、执行器注册表、工具 JSON 定义三者工具名集合相同。
 */
public enum FlowDesignToolNames {

    SEARCH_APIS("search_apis", true, true),
    GET_API_DETAIL("get_api_detail", true, true),
    GET_GRAPH_SUMMARY("get_graph_summary", true, true),
    GET_FLOW_META("get_flow_meta", true, true),
    LIST_PROJECT_ENVS("list_project_envs", true, true),
    /** 列举项目素材库：key、字段名、占位提示，不含明文；写 {{asset.*}} 前先调 */
    LIST_ASSET_VARIABLES("list_asset_variables", true, true),
    /**
     * 按 key 提出新增或更新素材库条目（只记提案，待用户确认后落盘）；回执不含明文。
     * 仅 Web 助手可见；MCP 不可调用。
     */
    UPSERT_ASSET_VARIABLES("upsert_asset_variables", true, false),
    /**
     * 向接口 design_hints 追加造流设计提示（直接落库）。
     * 仅 Web 助手可见；MCP 不可调用（只读经 get_api_detail）。
     */
    APPEND_API_DESIGN_HINTS("append_api_design_hints", true, false),
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
