package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
 * 加载 Web Agent 与 MCP 的 OpenAI function 定义（JSON Schema）。
 * <p>
 * Web：{@code ai/flow-design-tools.json}（11 个工具，含 submit 与 get_flow_api_health）+ 运行时注入 patch schema。<br>
 * MCP：主 JSON 中允许 MCP 调用的工具 + {@code ai/flow-design-mcp-extra-tools.json}（list_flows、get_flow），共 12 个只读工具。
 * <p>
 * 启动时核对：工具名枚举、执行器已注册工具、JSON 定义里的工具名三者集合相同，避免漏注册或多余工具。
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
     * <p>
     * 从 {@code flow-design-tools.json} 加载，并将 {@code flow-design-patch-schema.json}
     * 注入 {@code submit_flow_design_patch} 的 parameters。
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
                String schemaJson = FlowDesignPromptResources.loadText(FlowDesignPromptResources.PATCH_SCHEMA);
                JSONObject patchSchema = JSON.parseObject(schemaJson);
                JSONArray arr = JSON.parseArray(json);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject tool = arr.getJSONObject(i);
                    JSONObject fn = tool.getJSONObject("function");
                    if (fn != null && FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId().equals(fn.getString("name"))) {
                        fn.put("parameters", patchSchema);
                    }
                }
                cachedTools = arr.stream()
                        .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                        .toList();
                return cachedTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 AI tools 定义失败", e);
            }
        }
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
