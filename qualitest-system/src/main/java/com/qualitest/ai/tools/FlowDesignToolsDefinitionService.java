package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.mcp.McpProjectGates;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
import com.qualitest.ai.tools.plugin.PluginAiToolDefinitions;
import com.qualitest.ai.tools.plugin.PluginAiToolCatalog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 加载造流助手与 MCP 的工具定义（名称、描述、参数 Schema）。
 * <p>
 * 平台内置工具：启动时校验枚举、执行器注册名、JSON 清单三方名称集合完全一致（toolKind=builtin）。
 * 插件贡献工具：来自已启用插件说明书 {@code ai.tools[]}，每次按项目动态合并（toolKind=plugin）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDesignToolsDefinitionService {

    private final FlowDesignToolExecutor flowDesignToolExecutor;
    /** 可空：单测未注入时不合并动态插件工具 */
    private final PluginAiToolCatalog pluginAiToolCatalog;

    /** Web 造流助手工具定义缓存 */
    @Getter
    private volatile List<Map<String, Object>> cachedTools;

    /** MCP 只读工具定义缓存（内部 function 结构） */
    @Getter
    private volatile List<Map<String, Object>> cachedMcpTools;

    /** MCP tools/list 协议格式缓存：仅只读 */
    @Getter
    private volatile List<Map<String, Object>> cachedMcpProtocolTools;

    /** MCP tools/list 协议格式缓存：只读 + 全自动写工具 */
    @Getter
    private volatile List<Map<String, Object>> cachedMcpAutoWriteProtocolTools;

    /** MCP 协议工具原始定义缓存（function 结构，含只读、写流、导入） */
    private volatile List<Map<String, Object>> cachedMcpProtocolToolsRaw;

    /** 应用启动时校验工具名注册完整性 */
    @PostConstruct
    void validateToolRegistryConsistency() {
        try {
            validateToolRegistryConsistencyInternal();
        } catch (IOException e) {
            throw new IllegalStateException("加载工具定义失败", e);
        }
    }

    /**
     * 校验平台工具：枚举 ↔ 执行器注册名 ↔ Web/MCP JSON 清单名称集合一致。
     */
    private void validateToolRegistryConsistencyInternal() throws IOException {
        Set<String> declared = FlowDesignToolExecutor.allDeclaredToolNames();
        Set<String> registered = flowDesignToolExecutor.registeredToolNames();
        if (!declared.equals(registered)) {
            Set<String> missingInExecutor = new HashSet<>(declared);
            missingInExecutor.removeAll(registered);
            Set<String> extraInExecutor = new HashSet<>(registered);
            extraInExecutor.removeAll(declared);
            throw new IllegalStateException("FlowDesignToolNames 与 FlowDesignToolExecutor 注册不一致: "
                    + "missingInExecutor=" + missingInExecutor + ", extraInExecutor=" + extraInExecutor);
        }

        Set<String> webFromJson = new HashSet<>(extractToolNames(loadToolsDefinitionRaw()));
        Set<String> expectedWeb = FlowDesignToolNames.webAgentToolIds();
        if (!webFromJson.equals(expectedWeb)) {
            throw new IllegalStateException("flow-design-tools.json 与 FlowDesignToolNames.webAgent 不一致: "
                    + "jsonOnly=" + diff(webFromJson, expectedWeb)
                    + ", enumOnly=" + diff(expectedWeb, webFromJson));
        }

        Set<String> mcpFromJson = new HashSet<>(extractToolNames(loadMcpProtocolToolsRaw()));
        Set<String> expectedMcpAll = new HashSet<>(FlowDesignToolNames.mcpAllowedToolIds());
        expectedMcpAll.addAll(FlowDesignToolNames.mcpAutoWriteToolIds());
        expectedMcpAll.addAll(FlowDesignToolNames.mcpAutorunToolIds());
        expectedMcpAll.add(FlowDesignToolNames.IMPORT_APIS.getId());
        if (!mcpFromJson.equals(expectedMcpAll)) {
            throw new IllegalStateException("mcp-protocol-tools.json 与 MCP 可调用工具集不一致: "
                    + "jsonOnly=" + diff(mcpFromJson, expectedMcpAll)
                    + ", expectedOnly=" + diff(expectedMcpAll, mcpFromJson));
        }

        Set<String> missingInExecutorFromMcp = new HashSet<>(mcpFromJson);
        missingInExecutorFromMcp.removeAll(registered);
        if (!missingInExecutorFromMcp.isEmpty()) {
            throw new IllegalStateException("MCP 协议工具缺少执行器定义: " + missingInExecutorFromMcp);
        }

        log.info("Flow Design 工具注册校验通过: web={}, mcpProtocol={}",
                webFromJson.size(), mcpFromJson.size());
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> copy = new HashSet<>(a);
        copy.removeAll(b);
        return copy;
    }

    /**
     * Web Agent 可见的完整工具列表。
     * 从 tools 定义 JSON 加载，并对每条 description 做压缩以减小 Schema 体积。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadToolsDefinition() {
        List<Map<String, Object>> local = cachedTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedTools != null) {
                return cachedTools;
            }
            try {
                String json = FlowDesignPromptResources.loadText(FlowDesignPromptResources.TOOLS_DEFINITION);
                JSONArray arr = JSON.parseArray(json);
                cachedTools = arr.stream()
                        .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                        .map(FlowDesignToolsDefinitionService::compactToolDefinition)
                        .map(FlowDesignToolsDefinitionService::stampToolKind)
                        .toList();
                return cachedTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 AI tools 定义失败", e);
            }
        }
    }

    /**
     * 按是否全自动返回 Web 造流助手本轮工具定义。
     * false：去掉 run_test_flow；true：保留含跑流工具。
     */
    public List<Map<String, Object>> loadToolsDefinition(boolean autopilotEnabled) {
        return loadToolsDefinition(autopilotEnabled, null);
    }

    /**
     * Web 造流助手工具列表。
     * testProjectId 非空时追加该项目启用插件说明书贡献的 ai.tools。
     */
    public List<Map<String, Object>> loadToolsDefinition(boolean autopilotEnabled, Long testProjectId) {
        List<Map<String, Object>> all = loadToolsDefinition();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> tool : all) {
            String name = extractFunctionName(tool);
            if (!autopilotEnabled && FlowDesignToolNames.isAutopilotOnlyTool(name)) {
                continue;
            }
            filtered.add(tool);
        }
        appendContributedPluginTools(filtered, testProjectId);
        return List.copyOf(filtered);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadToolsDefinitionRaw() throws IOException {
        String json = FlowDesignPromptResources.loadText(FlowDesignPromptResources.TOOLS_DEFINITION);
        JSONArray arr = JSON.parseArray(json);
        return arr.stream()
                .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 加载 MCP 协议工具原始定义（含只读、写流、导入） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadMcpProtocolToolsRaw() throws IOException {
        List<Map<String, Object>> local = cachedMcpProtocolToolsRaw;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMcpProtocolToolsRaw != null) {
                return cachedMcpProtocolToolsRaw;
            }
            String json = FlowDesignPromptResources.loadText(FlowDesignPromptResources.MCP_PROTOCOL_TOOLS);
            JSONArray arr = JSON.parseArray(json);
            cachedMcpProtocolToolsRaw = arr.stream()
                    .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                    .collect(Collectors.toCollection(ArrayList::new));
            return cachedMcpProtocolToolsRaw;
        }
    }

    /**
     * MCP 默认只读工具列表（内部 function 结构：name / description / parameters）。
     */
    public List<Map<String, Object>> loadMcpToolsDefinition() {
        List<Map<String, Object>> local = cachedMcpTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMcpTools != null) {
                return cachedMcpTools;
            }
            try {
                List<Map<String, Object>> readonly = loadMcpProtocolToolsRaw().stream()
                        .filter(this::isMcpReadonlyToolDefinition)
                        .toList();
                cachedMcpTools = List.copyOf(readonly);
                return cachedMcpTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 MCP 协议 tools 定义失败", e);
            }
        }
    }

    /**
     * 返回 MCP tools/list 默认只读工具定义（协议格式：name / description / inputSchema）。
     */
    public List<Map<String, Object>> loadMcpProtocolTools() {
        List<Map<String, Object>> local = cachedMcpProtocolTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMcpProtocolTools != null) {
                return cachedMcpProtocolTools;
            }
            List<Map<String, Object>> protocolTools = new ArrayList<>();
            for (Map<String, Object> tool : loadMcpToolsDefinition()) {
                protocolTools.add(toMcpProtocolTool(tool));
            }
            cachedMcpProtocolTools = List.copyOf(protocolTools);
            return cachedMcpProtocolTools;
        }
    }

    /**
     * 组装 MCP tools/list。
     * 先按 mcp:flow:write 取只读或「只读+写流」列表，再按 mcp:api:import / mcp:run 追加对应工具。
     * 跑流工具仅在同时具备写流与跑流 MCP 权限时追加。
     *
     * @param mcpAutoWriteEnabled  true：列表含改图、新建流、素材/鉴权/环境写入等
     * @param mcpAutorunEnabled    true：在写流已开时追加 run_test_flow
     * @param mcpImportApisEnabled true：追加 import_apis
     */
    public List<Map<String, Object>> loadMcpProtocolTools(boolean mcpAutoWriteEnabled,
                                                          boolean mcpAutorunEnabled,
                                                          boolean mcpImportApisEnabled) {
        return loadMcpProtocolTools(mcpAutoWriteEnabled, mcpAutorunEnabled, mcpImportApisEnabled, null);
    }

    /**
     * 组装 MCP tools/list。
     * testProjectId 非空时追加插件说明书贡献的 ai.tools（协议格式）。
     */
    public List<Map<String, Object>> loadMcpProtocolTools(boolean mcpAutoWriteEnabled,
                                                          boolean mcpAutorunEnabled,
                                                          boolean mcpImportApisEnabled,
                                                          Long testProjectId) {
        List<Map<String, Object>> protocolTools = new ArrayList<>(
                mcpAutoWriteEnabled ? loadMcpAutoWriteProtocolToolsCached() : loadMcpProtocolTools());
        if (mcpImportApisEnabled) {
            appendMcpNamedTool(protocolTools, FlowDesignToolNames.IMPORT_APIS.getId(),
                    "加载 MCP import_apis 定义失败");
        }
        // 跑流工具须写流与跑流都开才出现在列表中
        if (mcpAutorunEnabled && mcpAutoWriteEnabled) {
            appendMcpNamedTool(protocolTools, FlowDesignToolNames.RUN_TEST_FLOW.getId(),
                    "加载 MCP run_test_flow 定义失败");
        }
        appendContributedPluginProtocolTools(protocolTools, testProjectId);
        return List.copyOf(protocolTools);
    }

    /**
     * 按项目门控快照组装 MCP tools/list。
     *
     * @param gates 成员角色 MCP 门控快照；null 按只读
     */
    public List<Map<String, Object>> loadMcpProtocolTools(McpProjectGates gates) {
        return loadMcpProtocolTools(gates, null);
    }

    /**
     * 按成员 MCP 门控与项目 id 组装协议工具列表。
     */
    public List<Map<String, Object>> loadMcpProtocolTools(McpProjectGates gates, Long testProjectId) {
        if (gates == null) {
            return loadMcpProtocolTools(false, false, false, testProjectId);
        }
        return loadMcpProtocolTools(
                gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled(), testProjectId);
    }

    /**
     * 回合会话的 tools/list。
     * 定义取自画布设计工具表，再转成 MCP 的 name / description / inputSchema。
     * 是否列入由回合会话可调用规则决定：关写流则无改图工具，关跑流则无 run_test_flow。
     * 不含列流、建流、导入接口、规程版本。导入开关不改变本列表。
     *
     * @param gates 成员角色 MCP 门控快照；null 按只读
     */
    public List<Map<String, Object>> loadTurnSessionTools(McpProjectGates gates) {
        return loadTurnSessionTools(gates, null);
    }

    /**
     * 回合会话 tools/list：画布设计工具表转协议格式，并按写流/跑流门控裁剪；
     * 插件贡献工具（toolKind=plugin）始终可列入（不改图）。
     */
    public List<Map<String, Object>> loadTurnSessionTools(McpProjectGates gates, Long testProjectId) {
        boolean write = gates != null && gates.autoWriteEnabled();
        boolean run = gates != null && gates.autorunEnabled();
        List<Map<String, Object>> protocolTools = new ArrayList<>();
        for (Map<String, Object> tool : loadToolsDefinition(true, testProjectId)) {
            String name = extractFunctionName(tool);
            if (isPluginKindTool(tool)) {
                protocolTools.add(toMcpProtocolTool(tool));
                continue;
            }
            if (!FlowDesignToolNames.isTurnSessionCallable(name, write, run)) {
                continue;
            }
            protocolTools.add(toMcpProtocolTool(tool));
        }
        return List.copyOf(protocolTools);
    }

    /** 把插件 ai.tools 以 Web function 结构并入列表 */
    private void appendContributedPluginTools(List<Map<String, Object>> tools, Long testProjectId) {
        appendContributedPluginDefinitions(
                tools,
                testProjectId,
                FlowDesignToolsDefinitionService::compactToolDefinition,
                FlowDesignToolsDefinitionService::extractFunctionName);
    }

    /** 把插件 ai.tools 转成 MCP 协议条目并入列表 */
    private void appendContributedPluginProtocolTools(List<Map<String, Object>> protocolTools, Long testProjectId) {
        appendContributedPluginDefinitions(
                protocolTools,
                testProjectId,
                FlowDesignToolsDefinitionService::toMcpProtocolTool,
                t -> String.valueOf(t.get("name")));
    }

    private void appendContributedPluginDefinitions(
            List<Map<String, Object>> target,
            Long testProjectId,
            java.util.function.Function<Map<String, Object>, Map<String, Object>> toEntry,
            java.util.function.Function<Map<String, Object>, String> entryName) {
        if (pluginAiToolCatalog == null || testProjectId == null || target == null) {
            return;
        }
        Set<String> existing = target.stream()
                .map(entryName)
                .filter(n -> n != null && !n.isBlank() && !"null".equals(n))
                .collect(Collectors.toCollection(HashSet::new));
        for (Map<String, Object> def : PluginAiToolDefinitions.buildAll(
                pluginAiToolCatalog.listContributedTools(testProjectId))) {
            Map<String, Object> entry = toEntry.apply(def);
            String name = entryName.apply(entry);
            if (name == null || name.isBlank() || "null".equals(name) || existing.contains(name)) {
                continue;
            }
            target.add(entry);
            existing.add(name);
        }
    }

    private static boolean isPluginKindTool(Map<String, Object> tool) {
        if (tool == null) {
            return false;
        }
        Object kind = tool.get(FlowDesignToolKind.JSON_FIELD);
        return FlowDesignToolKind.PLUGIN.wire().equals(kind);
    }

    /** 缓存「只读 + 自动写流」协议列表 */
    private List<Map<String, Object>> loadMcpAutoWriteProtocolToolsCached() {
        List<Map<String, Object>> local = cachedMcpAutoWriteProtocolTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMcpAutoWriteProtocolTools != null) {
                return cachedMcpAutoWriteProtocolTools;
            }
            try {
                List<Map<String, Object>> protocolTools = new ArrayList<>(loadMcpProtocolTools());
                Set<String> existing = protocolTools.stream()
                        .map(t -> String.valueOf(t.get("name")))
                        .collect(Collectors.toSet());
                for (Map<String, Object> tool : loadMcpProtocolToolsRaw()) {
                    String name = extractFunctionName(tool);
                    if (name == null || !FlowDesignToolNames.isMcpAutoWriteTool(name)
                            || existing.contains(name)) {
                        continue;
                    }
                    protocolTools.add(toMcpProtocolTool(tool));
                    existing.add(name);
                }
                cachedMcpAutoWriteProtocolTools = List.copyOf(protocolTools);
                return cachedMcpAutoWriteProtocolTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 MCP 自动写 tools 定义失败", e);
            }
        }
    }

    /**
     * 向协议工具列表追加指定名称工具（已存在则跳过）。
     *
     * @param protocolTools 协议工具列表
     * @param toolId        工具名
     * @param loadErrorMsg  加载失败时的异常文案
     */
    private void appendMcpNamedTool(List<Map<String, Object>> protocolTools, String toolId, String loadErrorMsg) {
        boolean already = protocolTools.stream()
                .anyMatch(t -> toolId.equals(String.valueOf(t.get("name"))));
        if (already) {
            return;
        }
        try {
            for (Map<String, Object> tool : loadMcpProtocolToolsRaw()) {
                String name = extractFunctionName(tool);
                if (toolId.equals(name)) {
                    protocolTools.add(toMcpProtocolTool(tool));
                    return;
                }
            }
        } catch (IOException e) {
            throw new LlmClientException(loadErrorMsg, e);
        }
    }

    /** 内部 function 结构转为 MCP 协议格式（name / description / inputSchema / toolKind） */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMcpProtocolTool(Map<String, Object> functionTool) {
        Object fnObj = functionTool.get("function");
        if (!(fnObj instanceof Map<?, ?> fnMap)) {
            throw new IllegalArgumentException("无效的工具定义：缺少 function");
        }
        Map<String, Object> fn = (Map<String, Object>) fnMap;
        Map<String, Object> mcpTool = new LinkedHashMap<>();
        String name = String.valueOf(fn.get("name"));
        mcpTool.put("name", name);
        mcpTool.put("description", fn.get("description"));
        mcpTool.put("inputSchema", fn.get("parameters"));
        Object kind = functionTool.get(FlowDesignToolKind.JSON_FIELD);
        if (kind != null) {
            mcpTool.put(FlowDesignToolKind.JSON_FIELD, kind);
        } else {
            // 静态清单无 toolKind 时一律视为平台内置
            mcpTool.put(FlowDesignToolKind.JSON_FIELD, FlowDesignToolKind.BUILTIN.wire());
        }
        return mcpTool;
    }

    /** 静态工具定义补齐顶层 toolKind=builtin（插件贡献定义自带 plugin） */
    private static Map<String, Object> stampToolKind(Map<String, Object> tool) {
        if (tool.containsKey(FlowDesignToolKind.JSON_FIELD)) {
            return tool;
        }
        Map<String, Object> next = new LinkedHashMap<>(tool);
        next.put(FlowDesignToolKind.JSON_FIELD, FlowDesignToolKind.BUILTIN.wire());
        return next;
    }

    /** 是否属于 MCP 默认只读工具定义 */
    private boolean isMcpReadonlyToolDefinition(Map<String, Object> tool) {
        String name = extractFunctionName(tool);
        return name != null && FlowDesignToolNames.isMcpAllowed(name);
    }

    /**
     * 缩短工具 description 里的重复套话，减小每轮注入模型的 Schema 体积。
     * 不改工具名与 parameters 结构。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> compactToolDefinition(Map<String, Object> tool) {
        Object fnObj = tool.get("function");
        if (!(fnObj instanceof Map<?, ?>)) {
            return tool;
        }
        Map<String, Object> fn = (Map<String, Object>) fnObj;
        Object descObj = fn.get("description");
        if (!(descObj instanceof String desc) || desc.isBlank()) {
            return tool;
        }
        String compact = desc
                .replace("AI 不造 input 节点。", "")
                .replace("（恰好 1 个 Staging 单元）", "")
                .replace("校验失败按 errors 再调本工具修正。", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (!compact.equals(desc)) {
            Map<String, Object> nextFn = new LinkedHashMap<>(fn);
            nextFn.put("description", compact);
            Map<String, Object> next = new LinkedHashMap<>(tool);
            next.put("function", nextFn);
            return next;
        }
        return tool;
    }

    /** 返回 Web 造流助手工具名列表（按定义文件中的声明顺序） */
    public List<String> listToolNames() {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> tool : loadToolsDefinition()) {
            String name = extractFunctionName(tool);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<String> extractToolNames(List<Map<String, Object>> tools) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            String name = extractFunctionName(tool);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static String extractFunctionName(Map<String, Object> tool) {
        Object fn = tool.get("function");
        if (!(fn instanceof Map<?, ?> fnMap)) {
            return null;
        }
        Object name = fnMap.get("name");
        return name != null ? String.valueOf(name) : null;
    }
}
