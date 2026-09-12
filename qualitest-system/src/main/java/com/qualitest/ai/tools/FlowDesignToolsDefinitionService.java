package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.qualitest.ai.llm.LlmClientException;
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
 * 加载 Web 造流与 MCP 的 OpenAI function 定义（名称、描述、参数 Schema）。
 * <p>
 * Web：主工具 JSON 中 webAgent 工具（含分类型 submit_*、素材列举与写入）。
 * MCP：主 JSON 中允许 MCP 的工具 + 额外列流/读流定义（只读，不含任何 submit）。
 * 启动时校验：工具名枚举、执行器已注册名、JSON 定义名三者集合相同。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDesignToolsDefinitionService {

    private final FlowDesignToolExecutor flowDesignToolExecutor;

    /** Web Agent 工具定义缓存 */
    @Getter
    private volatile List<Map<String, Object>> cachedTools;

    /** MCP 只读工具定义缓存（OpenAI function 结构，供内部转换） */
    @Getter
    private volatile List<Map<String, Object>> cachedMcpTools;

    /** MCP tools/list 协议格式缓存：{name, description, inputSchema} */
    @Getter
    private volatile List<Map<String, Object>> cachedMcpProtocolTools;

    @PostConstruct
    void validateToolRegistryConsistency() {
        try {
            validateToolRegistryConsistencyInternal();
        } catch (IOException e) {
            throw new IllegalStateException("加载 flow-design-tools 定义失败", e);
        }
    }

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

        Set<String> mcpFromJson = new HashSet<>();
        mcpFromJson.addAll(extractToolNames(loadToolsDefinitionRaw().stream()
                .filter(this::isMcpToolDefinition)
                .toList()));
        mcpFromJson.addAll(extractToolNames(loadMcpExtraToolsDefinition()));
        Set<String> expectedMcp = FlowDesignToolNames.mcpAllowedToolIds();
        if (!mcpFromJson.equals(expectedMcp)) {
            throw new IllegalStateException("MCP 工具 JSON 与 FlowDesignToolNames.mcpAllowed 不一致: "
                    + "jsonOnly=" + diff(mcpFromJson, expectedMcp)
                    + ", enumOnly=" + diff(expectedMcp, mcpFromJson));
        }
        log.info("Flow Design 工具注册一致性校验通过: web={}, mcp={}", webFromJson.size(), mcpFromJson.size());
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
     * Web Agent 本轮工具列表。
     * autopilotEnabled=false 时剔除 run_test_flow。
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

    /**
     * MCP 网关对外暴露的工具列表。
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
            List<Map<String, Object>> merged = new ArrayList<>();
            merged.addAll(loadToolsDefinition().stream()
                    .filter(this::isMcpToolDefinition)
                    .toList());
            merged.addAll(loadMcpExtraToolsDefinition());
            cachedMcpTools = List.copyOf(merged);
            return cachedMcpTools;
        }
    }

    /**
     * MCP {@code tools/list} 直接可用的工具定义。
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadMcpExtraToolsDefinition() {
        try {
            String json = FlowDesignPromptResources.loadText(FlowDesignPromptResources.MCP_EXTRA_TOOLS);
            JSONArray arr = JSON.parseArray(json);
            return arr.stream()
                    .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                    .toList();
        } catch (IOException e) {
            throw new LlmClientException("加载 MCP 扩展 tools 定义失败", e);
        }
    }

    private boolean isMcpToolDefinition(Map<String, Object> tool) {
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

    /** 返回 Web Agent 工具名列表（按 tools 定义文件中的声明顺序） */
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
