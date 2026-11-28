package com.qualitest.plugin.manifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从插件说明书 ai.tools 解析 Function Tool 贡献列表。
 * <p>
 * 读取路径：manifest.ai.tools。缺 ai / tools 时返回空列表。
 * 工具名须匹配 {@code ^[a-zA-Z][a-zA-Z0-9_]*$}；capability 须属于本包 capabilities。
 */
public final class PluginAiToolsSupport {

    private static final Pattern TOOL_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    private PluginAiToolsSupport() {
    }

    /**
     * 解析说明书中的 AI 工具列表；非法项跳过。
     */
    @SuppressWarnings("unchecked")
    public static List<PluginAiTool> listTools(PluginManifest manifest) {
        if (manifest == null || manifest.getAi() == null) {
            return List.of();
        }
        Object toolsObj = manifest.getAi().get("tools");
        if (!(toolsObj instanceof List<?> rawList)) {
            return List.of();
        }
        List<PluginAiTool> out = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) raw;
            String name = str(m.get("name"));
            String capability = str(m.get("capability"));
            if (name.isEmpty() || !TOOL_NAME.matcher(name).matches()) {
                continue;
            }
            if (capability.isEmpty() || manifest.findCapability(capability) == null) {
                continue;
            }
            Map<String, Object> parameters = null;
            Object paramsObj = m.get("parameters");
            if (paramsObj instanceof Map<?, ?> pm) {
                parameters = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : pm.entrySet()) {
                    if (e.getKey() != null) {
                        parameters.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
            String description = str(m.get("description"));
            if (description.isEmpty()) {
                description = "调用插件 " + manifest.getId() + " 能力 " + capability;
            }
            out.add(PluginAiTool.builder()
                    .name(name)
                    .description(description)
                    .capability(capability)
                    .parameters(parameters)
                    .build());
        }
        return List.copyOf(out);
    }

    /** 按工具名查找；找不到返回 null */
    public static PluginAiTool findByName(PluginManifest manifest, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        String n = toolName.trim();
        for (PluginAiTool t : listTools(manifest)) {
            if (n.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
