package com.qualitest.ai.tools;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流 AI 设计工具名注册表。
 * <p>
 * 每个工具两个开关：webAgent（是否进 Web 造流助手工具列表）、
 * mcpAllowed（是否默认允许 MCP 调用，一般为只读勘察工具）。
 * 改图 submit、create_flow、素材/鉴权写入、跑流等默认不进 MCP；
 * 仅当项目开启「允许 MCP 全自动写流」后，由运行时追加进工具列表并可调用。
 */
public enum FlowDesignToolNames {

    /** 按关键词搜索当前项目下的接口，返回 id、method、path、名称与鉴权摘要 */
    SEARCH_APIS("search_apis", true, true),
    /** 批量拉取接口造流摘要（参数、schema、designHints、建议 extracts 等） */
    GET_API_DETAILS("get_api_details", true, true),
    /** 获取当前画布节点与边摘要，含开始节点数与异常拓扑提示 */
    GET_GRAPH_SUMMARY("get_graph_summary", true, true),
    /** 获取画布 meta：运行场景列表、flow 输出名、开始节点、当前默认场景 id */
    GET_FLOW_META("get_flow_meta", true, true),
    /** 列举项目环境 id、名称、URL 与环境变量键名（不含值） */
    LIST_PROJECT_ENVS("list_project_envs", true, true),
    /** 列举项目素材库变量键与字段名（不含明文） */
    LIST_ASSET_VARIABLES("list_asset_variables", true, true),
    /** 新增或更新素材库条目；半自动进提案，全自动工具内直接写库 */
    UPSERT_ASSET_VARIABLES("upsert_asset_variables", true, false),
    /** 列举多端配置 Profile（pathPrefix、托管头、响应约定、凭证目标；无密钥明文） */
    LIST_PROJECT_AUTH_PROFILES("list_project_auth_profiles", true, true),
    /** 浅合并更新或新建多端 Profile（鉴权头 / 响应约定 / credentialApi）；半自动进提案，全自动工具内直接写库 */
    UPSERT_AUTH_PROFILE("upsert_auth_profile", true, false),
    /** 向接口 design_hints 追加短提示并直接落库 */
    APPEND_API_DESIGN_HINTS("append_api_design_hints", true, false),
    /**
     * MCP 专用：按结构化 items 导入或更新项目接口库（方法+path 幂等写入）。
     * 不进入 Web 造流助手列表；默认也不在 MCP 只读白名单。
     * 仅当项目开启「允许 MCP 导入接口」时，才会出现在 tools/list 并允许 tools/call。
     * 本工具不走「允许 MCP 全自动写流」开关。
     */
    IMPORT_APIS("import_apis", false, false),
    /** 读取单个节点的 type 与完整 data */
    GET_NODE_DETAIL("get_node_detail", true, true),
    /** 读取单条边的 id、source、target、label */
    GET_EDGE_DETAIL("get_edge_detail", true, true),
    /** 读取单个运行场景配置（含 flowSeed 键名，不含明文） */
    GET_SCENARIO_DETAIL("get_scenario_detail", true, true),
    /** 读取某次 Run 的失败步骤现场（按失败类别分区） */
    GET_RUN_FAILURE("get_run_failure", true, true),
    /** 检查画布上项目 HTTP 节点的 API 语义健康告警 */
    GET_FLOW_API_HEALTH("get_flow_api_health", true, true),
    /** 列举可引用的子流模板与项目内测试流摘要 */
    LIST_SUBFLOW_TEMPLATES("list_subflow_templates", true, true),
    /** 读取指定测试流的拓扑摘要与 flowOutputs */
    GET_SUBFLOW_DETAIL("get_subflow_detail", true, true),
    /** 仅 MCP：按项目列举测试流 */
    LIST_FLOWS("list_flows", false, true),
    /** 仅 MCP：读取完整测试流与 graphJson */
    GET_FLOW("get_flow", false, true),
    /** 仅 MCP：返回造流规程版本指纹（只读，始终可调用） */
    GET_MCP_GUIDE_VERSION("get_mcp_guide_version", false, true),
    /** 仅 MCP：新建空画布测试流（须项目开启 MCP 全自动写流） */
    CREATE_FLOW("create_flow", false, false),

    /** 新增或修改单个 HTTP 节点（每次调用一个 Staging 单元） */
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
    /** 删除单个节点、边或运行场景 */
    SUBMIT_DELETE("submit_delete", true, false),

    /**
     * 触发当前测试流 Run 并返回结果摘要。
     * 仅全自动上下文可调用；跑前会把未落盘的 submit 单元先写入库。
     */
    RUN_TEST_FLOW("run_test_flow", true, false);

    /** 工具名字符串，作 Function Calling / 执行器路由键 */
    private final String id;
    /** 是否进入 Web 造流助手工具列表 */
    private final boolean webAgent;
    /** 是否默认允许 MCP 调用（只读勘察类为 true） */
    private final boolean mcpAllowed;

    FlowDesignToolNames(String id, boolean webAgent, boolean mcpAllowed) {
        this.id = id;
        this.webAgent = webAgent;
        this.mcpAllowed = mcpAllowed;
    }

    /** 工具名字符串 */
    public String getId() {
        return id;
    }

    /** 是否进入 Web 造流助手工具列表 */
    public boolean isWebAgent() {
        return webAgent;
    }

    /** 是否默认允许 MCP 调用 */
    public boolean isMcpAllowed() {
        return mcpAllowed;
    }

    /**
     * 是否为画布单元提交类工具（名称以 submit_ 开头）。
     */
    public static boolean isSubmitUnitTool(String name) {
        return name != null && name.startsWith("submit_");
    }

    /**
     * 是否为仅全自动才注入 Web 助手的工具。
     * 当前仅 run_test_flow。
     */
    public static boolean isAutopilotOnlyTool(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return RUN_TEST_FLOW.id.equals(name);
    }

    /**
     * 是否为「导入项目接口」工具名（import_apis）。
     * 该工具由项目的「允许 MCP 导入接口」开关单独控制。
     */
    public static boolean isMcpImportApisTool(String name) {
        return IMPORT_APIS.id.equals(name);
    }

    /**
     * 是否为 MCP 全自动写流类工具。
     * 需项目开启「允许 MCP 全自动写流」后，才可列入 tools/list 并接受 tools/call。
     * 包括：全部 submit_*、create_flow、素材写入、鉴权写入、追加接口设计提示、跑流。
     * 不包括 import_apis。
     */
    public static boolean isMcpAutopilotWriteTool(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (isSubmitUnitTool(name)) {
            return true;
        }
        return CREATE_FLOW.id.equals(name)
                || UPSERT_ASSET_VARIABLES.id.equals(name)
                || UPSERT_AUTH_PROFILE.id.equals(name)
                || APPEND_API_DESIGN_HINTS.id.equals(name)
                || RUN_TEST_FLOW.id.equals(name);
    }

    /** 按工具名判断是否默认允许 MCP（只读集） */
    public static boolean isMcpAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.mcpAllowed);
    }

    /**
     * 判断当前请求是否允许经 MCP 调用该工具。
     * <ul>
     *   <li>只读白名单工具：始终允许</li>
     *   <li>import_apis：仅当 importApisEnabled 为 true</li>
     *   <li>写流类工具：仅当 autopilotEnabled 为 true</li>
     * </ul>
     *
     * @param autopilotEnabled  是否开启「允许 MCP 全自动写流」
     * @param importApisEnabled 是否开启「允许 MCP 导入接口」
     */
    public static boolean isMcpCallable(String name, boolean autopilotEnabled, boolean importApisEnabled) {
        if (isMcpAllowed(name)) {
            return true;
        }
        if (isMcpImportApisTool(name)) {
            return importApisEnabled;
        }
        return autopilotEnabled && isMcpAutopilotWriteTool(name);
    }

    /**
     * 仅根据写流开关判断是否可调用；导入接口按未开启处理。
     *
     * @param autopilotEnabled 是否开启「允许 MCP 全自动写流」
     */
    public static boolean isMcpCallable(String name, boolean autopilotEnabled) {
        return isMcpCallable(name, autopilotEnabled, false);
    }

    /** 按工具名判断是否属于 Web 造流助手 */
    public static boolean isWebAgent(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.id.equals(name) && t.webAgent);
    }

    /** Web 造流助手应加载的全部工具 id */
    public static Set<String> webAgentToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isWebAgent)
                .map(FlowDesignToolNames::getId)
                .collect(Collectors.toSet());
    }

    /** MCP 默认只读工具 id 集合 */
    public static Set<String> mcpAllowedToolIds() {
        return Arrays.stream(values())
                .filter(FlowDesignToolNames::isMcpAllowed)
                .map(FlowDesignToolNames::getId)
                .collect(Collectors.toSet());
    }

    /** MCP 全自动写工具 id 集合（不含只读） */
    public static Set<String> mcpAutopilotWriteToolIds() {
        return Arrays.stream(values())
                .map(FlowDesignToolNames::getId)
                .filter(FlowDesignToolNames::isMcpAutopilotWriteTool)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** MCP 全自动开启时的全部工具 id（只读加写） */
    public static Set<String> mcpAutopilotToolIds() {
        Set<String> ids = new LinkedHashSet<>(mcpAllowedToolIds());
        ids.addAll(mcpAutopilotWriteToolIds());
        return ids;
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
