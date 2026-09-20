package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.mcp.McpToolInvokeService.McpProjectGates;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
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
 * 加载造流助手与 MCP 协议的工具定义（名称、描述、参数 Schema）。
 * <p>
 * 造流助手：读助手工具清单文件，按是否全自动裁剪跑流工具。
 * MCP：读协议工具清单文件，再按项目写流/跑流/导入开关裁剪可见列表。
 * 启动时校验枚举、执行器注册名与 JSON 工具名集合齐全、无多余项。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDesignToolsDefinitionService {

    private final FlowDesignToolExecutor flowDesignToolExecutor;

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

    /** 校验枚举、执行器、JSON 三处工具名集合齐全且无多余项 */
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
                        .toList();
                return cachedTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 AI tools 定义失败", e);
            }
        }
    }

    /**
     * 按是否全自动返回 Web Agent 本轮可用工具定义。
     * false：去掉 run_test_flow（半自动不能跑流）。
     * true：保留全部 Web 工具含 run_test_flow。
     */
    public List<Map<String, Object>> loadToolsDefinition(boolean autopilotEnabled) {
        List<Map<String, Object>> all = loadToolsDefinition();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> tool : all) {
            String name = extractFunctionName(tool);
            if (!autopilotEnabled && FlowDesignToolNames.isAutopilotOnlyTool(name)) {
                continue;
            }
            filtered.add(tool);
        }
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
     * 组装 MCP tools/list。参数顺序与 guideVersion 一致：写流 → 跑流 → 导入。
     *
     * @param mcpAutoWriteEnabled  true：追加写流工具
     * @param mcpAutorunEnabled    true：在写流已开时追加 run_test_flow
     * @param mcpImportApisEnabled true：追加 import_apis
     */
    public List<Map<String, Object>> loadMcpProtocolTools(boolean mcpAutoWriteEnabled,
                                                          boolean mcpAutorunEnabled,
                                                          boolean mcpImportApisEnabled) {
        List<Map<String, Object>> protocolTools = new ArrayList<>(
                mcpAutoWriteEnabled ? loadMcpAutoWriteProtocolToolsCached() : loadMcpProtocolTools());
        if (mcpImportApisEnabled) {
            appendMcpNamedTool(protocolTools, FlowDesignToolNames.IMPORT_APIS.getId(),
                    "加载 MCP import_apis 定义失败");
        }
        // 跑流依赖写流：仅两开关都开时列出 run_test_flow
        if (mcpAutorunEnabled && mcpAutoWriteEnabled) {
            appendMcpNamedTool(protocolTools, FlowDesignToolNames.RUN_TEST_FLOW.getId(),
                    "加载 MCP run_test_flow 定义失败");
        }
        return List.copyOf(protocolTools);
    }

    /**
     * 按项目门控快照组装 MCP tools/list。
     *
     * @param gates 写流 / 跑流 / 导入三道开关快照；null 按只读
     */
    public List<Map<String, Object>> loadMcpProtocolTools(McpProjectGates gates) {
        if (gates == null) {
            return loadMcpProtocolTools();
        }
        return loadMcpProtocolTools(
                gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled());
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

    /** 内部 function 结构转为 MCP 协议格式（name / description / inputSchema） */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMcpProtocolTool(Map<String, Object> functionTool) {
        Object fnObj = functionTool.get("function");
        if (!(fnObj instanceof Map<?, ?> fnMap)) {
            throw new IllegalArgumentException("无效的工具定义：缺少 function");
        }
        Map<String, Object> fn = (Map<String, Object>) fnMap;
        Map<String, Object> mcpTool = new LinkedHashMap<>();
        mcpTool.put("name", String.valueOf(fn.get("name")));
        mcpTool.put("description", fn.get("description"));
        mcpTool.put("inputSchema", fn.get("parameters"));
        return mcpTool;
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
