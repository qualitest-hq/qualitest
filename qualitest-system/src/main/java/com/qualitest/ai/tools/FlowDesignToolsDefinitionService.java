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
 * 加载 Web 造流与 MCP 的工具定义（名称、描述、参数 Schema）。
 * <p>
 * Web：造流助手可用工具（含 submit、素材写入、跑流等）。
 * MCP 默认：只读勘察工具列表。
 * MCP 全自动：在只读列表上追加改图 submit、素材/鉴权写入、跑流等写工具（须项目开关开启）。
 * 启动时校验：枚举声明名、执行器已注册名、JSON 定义名集合齐全无多余。
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
    private volatile List<Map<String, Object>> cachedMcpAutopilotProtocolTools;

    /** 应用启动时校验工具名注册完整性 */
    @PostConstruct
    void validateToolRegistryConsistency() {
        try {
            validateToolRegistryConsistencyInternal();
        } catch (IOException e) {
            throw new IllegalStateException("加载 flow-design-tools 定义失败", e);
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

        Set<String> mcpFromJson = new HashSet<>();
        mcpFromJson.addAll(extractToolNames(loadToolsDefinitionRaw().stream()
                .filter(this::isMcpReadonlyToolDefinition)
                .toList()));
        mcpFromJson.addAll(extractToolNames(loadMcpExtraToolsDefinition().stream()
                .filter(this::isMcpReadonlyToolDefinition)
                .toList()));
        Set<String> expectedMcp = FlowDesignToolNames.mcpAllowedToolIds();
        if (!mcpFromJson.equals(expectedMcp)) {
            throw new IllegalStateException("MCP 工具 JSON 与 FlowDesignToolNames.mcpAllowed 不一致: "
                    + "jsonOnly=" + diff(mcpFromJson, expectedMcp)
                    + ", enumOnly=" + diff(expectedMcp, mcpFromJson));
        }

        Set<String> writeIds = FlowDesignToolNames.mcpAutopilotWriteToolIds();
        Set<String> missingWrite = new HashSet<>(writeIds);
        missingWrite.removeAll(registered);
        if (!missingWrite.isEmpty()) {
            throw new IllegalStateException("MCP 全自动写工具缺少执行器定义: " + missingWrite);
        }

        Set<String> writeSchemaNames = new HashSet<>();
        writeSchemaNames.addAll(extractToolNames(loadToolsDefinitionRaw()).stream()
                .filter(FlowDesignToolNames::isMcpAutopilotWriteTool)
                .toList());
        writeSchemaNames.addAll(extractToolNames(loadMcpExtraToolsDefinition()).stream()
                .filter(FlowDesignToolNames::isMcpAutopilotWriteTool)
                .toList());
        Set<String> missingWriteSchema = new HashSet<>(writeIds);
        missingWriteSchema.removeAll(writeSchemaNames);
        if (!missingWriteSchema.isEmpty()) {
            throw new IllegalStateException("MCP 全自动写工具缺少 JSON Schema 定义: " + missingWriteSchema);
        }
        log.info("Flow Design 工具注册一致性校验通过: web={}, mcpReadonly={}, mcpWrite={}",
                webFromJson.size(), mcpFromJson.size(), writeIds.size());
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
            // 主清单里的只读工具 + MCP 专用只读扩展（列流、读流；不含 create_flow 等写工具）
            List<Map<String, Object>> merged = new ArrayList<>();
            merged.addAll(loadToolsDefinition().stream()
                    .filter(this::isMcpReadonlyToolDefinition)
                    .toList());
            merged.addAll(loadMcpExtraToolsDefinition().stream()
                    .filter(this::isMcpReadonlyToolDefinition)
                    .toList());
            cachedMcpTools = List.copyOf(merged);
            return cachedMcpTools;
        }
    }

    /**
     * 返回 MCP tools/list 用的工具定义。
     *
     * @param mcpAutopilotEnabled true 时在只读列表上追加改图/写入/跑流等写工具
     */
    public List<Map<String, Object>> loadMcpProtocolTools(boolean mcpAutopilotEnabled) {
        if (!mcpAutopilotEnabled) {
            return loadMcpProtocolTools();
        }
        List<Map<String, Object>> local = cachedMcpAutopilotProtocolTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMcpAutopilotProtocolTools != null) {
                return cachedMcpAutopilotProtocolTools;
            }
            // 以只读列表为底，再挂上写工具并标注 MCP 落盘说明（含 mcp-extra 的 create_flow）
            List<Map<String, Object>> protocolTools = new ArrayList<>(loadMcpProtocolTools());
            Set<String> existing = protocolTools.stream()
                    .map(t -> String.valueOf(t.get("name")))
                    .collect(Collectors.toSet());
            appendMcpWriteTools(protocolTools, existing, loadToolsDefinition());
            appendMcpWriteTools(protocolTools, existing, loadMcpExtraToolsDefinition());
            cachedMcpAutopilotProtocolTools = List.copyOf(protocolTools);
            return cachedMcpAutopilotProtocolTools;
        }
    }

    /**
     * 把源列表中的 MCP 全自动写工具转为协议格式并追加到 protocolTools。
     */
    private static void appendMcpWriteTools(List<Map<String, Object>> protocolTools,
                                            Set<String> existing,
                                            List<Map<String, Object>> source) {
        for (Map<String, Object> tool : source) {
            String name = extractFunctionName(tool);
            if (name == null || !FlowDesignToolNames.isMcpAutopilotWriteTool(name) || existing.contains(name)) {
                continue;
            }
            protocolTools.add(annotateMcpWriteTool(toMcpProtocolTool(tool)));
            existing.add(name);
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
     * 给 MCP 写工具 description 追加落盘说明，避免模型以为还要单独 commit。
     * submit：成功即写库；run：跑库中最新图；其它写入：工具内直接写库。
     */
    private static Map<String, Object> annotateMcpWriteTool(Map<String, Object> mcpTool) {
        Object nameObj = mcpTool.get("name");
        String name = nameObj != null ? String.valueOf(nameObj) : "";
        Object descObj = mcpTool.get("description");
        String desc = descObj instanceof String s ? s : "";
        String suffix;
        if (FlowDesignToolNames.isSubmitUnitTool(name)) {
            suffix = "【MCP 全自动】本工具成功后立即写入测试流库，无需再 commit。";
        } else if (FlowDesignToolNames.RUN_TEST_FLOW.getId().equals(name)) {
            suffix = "【MCP 全自动】跑库中最新图；此前每次 submit 已落盘。";
        } else {
            suffix = "【MCP 全自动】工具内直接写库。";
        }
        Map<String, Object> next = new LinkedHashMap<>(mcpTool);
        next.put("description", (desc + " " + suffix).trim());
        return next;
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

    /** 加载仅 MCP 使用的扩展工具定义（如列流、读流） */
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
