package com.qualitest.ai.tools;

import java.util.Arrays;

/**
 * 测试流 AI 设计工具名注册表。
 * <p>
 * 每个工具两个开关：webAgent（是否进 Web 造流助手工具列表）、mcpAllowed（是否允许 MCP 调用）。
 * submit_* 仅 Web：半自动经 Staging 确认后才落库；全自动由服务端隐式写库。
 * run_test_flow 仅 Web，且仅当请求开启全自动时注入模型；MCP 始终只读。
 * 启动时校验：本枚举、执行器注册表、flow-design-tools.json 三者工具名集合必须相同。
 */
public enum FlowDesignToolNames {

    /** 按关键词搜索当前项目下的接口，返回 id、method、path、名称与鉴权摘要 */
    SEARCH_APIS("search_apis", true, true),
    /** 批量拉取接口造流摘要（参数、schema、designHints、建议 extracts 等），每批最多若干条 */
    GET_API_DETAILS("get_api_details", true, true),
    /** 获取当前画布节点与边摘要，含开始节点数与异常拓扑提示 */
    GET_GRAPH_SUMMARY("get_graph_summary", true, true),
    /** 获取画布 meta：运行场景列表、flow 输出名、开始节点、当前默认场景 id */
    GET_FLOW_META("get_flow_meta", true, true),
    /** 列举项目环境 id、名称、URL 与环境变量键名（不含值） */
    LIST_PROJECT_ENVS("list_project_envs", true, true),
    /** 列举项目素材库变量键与字段名（不含明文） */
    LIST_ASSET_VARIABLES("list_asset_variables", true, true),
    /** 新增或更新素材库条目（半自动：提案待确认；全自动：工具内直写） */
    UPSERT_ASSET_VARIABLES("upsert_asset_variables", true, false),
    /** 向接口 design_hints 追加短提示并直接落库 */
    APPEND_API_DESIGN_HINTS("append_api_design_hints", true, false),
    /** 读取单个节点的 type 与完整 data */
    GET_NODE_DETAIL("get_node_detail", true, true),
    /** 读取单条边的 id、source、target、label */
    GET_EDGE_DETAIL("get_edge_detail", true, true),
    /** 读取单个运行场景配置（含 flowSeed 键名，不含明文） */
    GET_SCENARIO_DETAIL("get_scenario_detail", true, true),
    /** 读取某次 Run 的失败步骤现场（按失败类别分区） */
    GET_RUN_FAILURE("get_run_failure", true, true),
    /** 检查画布上项目 HTTP 节点的 API 语义健康告警（缺失、孤儿测值、抽取路径失效等） */
    GET_FLOW_API_HEALTH("get_flow_api_health", true, true),
    /** 列举可引用的子流模板与项目内测试流摘要 */
    LIST_SUBFLOW_TEMPLATES("list_subflow_templates", true, true),
    /** 读取指定测试流的拓扑摘要与 flowOutputs */
    GET_SUBFLOW_DETAIL("get_subflow_detail", true, true),
    /** 仅 MCP：按项目列举测试流 */
    LIST_FLOWS("list_flows", false, true),
    /** 仅 MCP：读取完整测试流与 graphJson */
    GET_FLOW("get_flow", false, true),

    /**
     * 以下为 Web 画布写工具：每次调用恰好产出 1 个 Staging 单元。
     * 节点/边/场景用 upsert（参数 op=add|update）；删除用统一 submit_delete（kind+id）。
     */
    /** 新增或修改单个 HTTP 节点 */
    SUBMIT_HTTP_NODE("submit_http_node", true, false),
    /** 新增或修改单个断言节点 */
    SUBMIT_ASSERT_NODE("submit_assert_node", true, false),
    /** 新增或修改单个条件分支节点 */
    SUBMIT_CONDITION_NODE("submit_condition_node", true, false),
    /** 新增或修改单个赋值节点 */
    SUBMIT_ASSIGN_NODE("submit_assign_node", true, false),
    /** 新增或修改单个延时节点 */
    SUBMIT_DELAY_NODE("submit_delay_node", true, false),
    /** 新增或修改单个脚本节点 */
    SUBMIT_SCRIPT_NODE("submit_script_node", true, false),
    /** 新增或修改单个子流节点 */
    SUBMIT_SUBFLOW_NODE("submit_subflow_node", true, false),

    /** 新增或修改单条边 */
    SUBMIT_EDGE("submit_edge", true, false),
    /** 新增或修改单个运行场景（不切换画布默认场景） */
    SUBMIT_SCENARIO("submit_scenario", true, false),
    /** 建议删除单个节点、边或运行场景 */
    SUBMIT_DELETE("submit_delete", true, false),

    /**
     * 全自动：触发当前测试流 Run 并返回结果摘要（仅 Web，须请求 autopilotEnabled；跑前自动落盘）。
     */
    RUN_TEST_FLOW("run_test_flow", true, false);

    private final String id;
    private final boolean webAgent;
    private final boolean mcpAllowed;

    FlowDesignToolNames(String id, boolean webAgent, boolean mcpAllowed) {
        this.id = id;
        this.webAgent = webAgent;
        this.mcpAllowed = mcpAllowed;
    }

    /** Function Calling 工具名，亦作执行器路由键 */
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
     * 名称以 submit_ 开头即视为写图单元工具。
     */
    public static boolean isSubmitUnitTool(String name) {
        return name != null && name.startsWith("submit_");
    }

    /**
     * 全自动才注入给模型的工具名判定。
     * 当前仅 run_test_flow；改图落盘不通过独立工具暴露给模型。
     */
    public static boolean isAutopilotOnlyTool(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return RUN_TEST_FLOW.id.equals(name);
    }

    /** 按工具名判断是否允许 MCP */
    public static boolean isMcpAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.mcpAllowed);
    }

    /** 按工具名判断是否属于 Web Agent */
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
