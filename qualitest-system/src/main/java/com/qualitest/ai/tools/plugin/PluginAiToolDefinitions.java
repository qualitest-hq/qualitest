package com.qualitest.ai.tools.plugin;

import com.qualitest.ai.tools.FlowDesignToolKind;
import com.qualitest.plugin.manifest.PluginAiTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把插件说明书 ai.tools 转成造流 / MCP 的 Function Calling 定义（type=function）。
 * <p>
 * 顶层带 {@link FlowDesignToolKind#PLUGIN}；parameters 缺省时补最小 object schema，并可选附带 envId。
 */
public final class PluginAiToolDefinitions {

    private PluginAiToolDefinitions() {
    }

    /**
     * 为一条贡献生成工具定义。
     */
    public static Map<String, Object> buildFunctionTool(PluginAiToolCatalog.ContributedTool contributed) {
        if (contributed == null || contributed.tool() == null) {
            return null;
        }
        PluginAiTool tool = contributed.tool();
        Map<String, Object> parameters = buildParameters(tool);

        String display = contributed.displayName() != null && !contributed.displayName().isBlank()
                ? contributed.displayName()
                : contributed.pluginId();
        String description = tool.getDescription()
                + " [plugin=" + contributed.pluginId()
                + ", capability=" + tool.getCapability()
                + ", " + display
                + "] 当场 Invoke，不改图。";

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.getName());
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("type", "function");
        def.put(FlowDesignToolKind.JSON_FIELD, FlowDesignToolKind.PLUGIN.wire());
        def.put("function", function);
        return def;
    }

    public static List<Map<String, Object>> buildAll(List<PluginAiToolCatalog.ContributedTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PluginAiToolCatalog.ContributedTool t : tools) {
            Map<String, Object> def = buildFunctionTool(t);
            if (def != null) {
                out.add(def);
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildParameters(PluginAiTool tool) {
        Map<String, Object> parameters;
        if (tool.hasParameters()) {
            parameters = new LinkedHashMap<>(tool.getParameters());
        } else {
            parameters = new LinkedHashMap<>();
            parameters.put("type", "object");
            parameters.put("properties", new LinkedHashMap<String, Object>());
        }
        Object propsObj = parameters.get("properties");
        Map<String, Object> properties;
        if (propsObj instanceof Map<?, ?> pm) {
            properties = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                if (e.getKey() != null) {
                    properties.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } else {
            properties = new LinkedHashMap<>();
        }
        if (!properties.containsKey("envId")) {
            Map<String, Object> envId = new LinkedHashMap<>();
            envId.put("type", "string");
            envId.put("description", "测试环境 id；可省略则回退造流上下文默认环境");
            properties.put("envId", envId);
        }
        parameters.put("properties", properties);
        if (!parameters.containsKey("type")) {
            parameters.put("type", "object");
        }
        return parameters;
    }
}
