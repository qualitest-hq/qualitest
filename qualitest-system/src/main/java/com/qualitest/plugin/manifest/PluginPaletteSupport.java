package com.qualitest.plugin.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从插件说明书 ui 段解析画布 palette 贡献列表。
 * <p>
 * 读取路径：manifest.ui.palette.nodes。缺 ui / palette / nodes 时返回空列表。
 * 单项缺 id 或 capability 则跳过。argsSchema 为空时回退读取同项上的 configSchema。
 */
public final class PluginPaletteSupport {

    private PluginPaletteSupport() {
    }

    /**
     * 解析说明书中的 palette 节点列表。
     *
     * @param manifest 已解析说明书，可为 null
     * @return 不可变列表；无贡献时为空
     */
    @SuppressWarnings("unchecked")
    public static List<PluginPaletteNode> listNodes(PluginManifest manifest) {
        if (manifest == null || manifest.getUi() == null) {
            return List.of();
        }
        Object paletteObj = manifest.getUi().get("palette");
        if (!(paletteObj instanceof Map<?, ?> palette)) {
            return List.of();
        }
        Object nodesObj = palette.get("nodes");
        if (!(nodesObj instanceof List<?> rawList)) {
            return List.of();
        }
        List<PluginPaletteNode> out = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String id = str(m.get("id"));
            String capability = str(m.get("capability"));
            if (id.isEmpty() || capability.isEmpty()) {
                continue;
            }
            String label = str(m.get("label"));
            if (label.isEmpty()) {
                label = id;
            }
            Map<String, Object> argsSchema = coerceSchema(m.get("argsSchema"));
            if (argsSchema.isEmpty()) {
                // 旧草稿字段名：与 argsSchema 同义
                argsSchema = coerceSchema(m.get("configSchema"));
            }
            out.add(PluginPaletteNode.builder()
                    .id(id)
                    .label(label)
                    .capability(capability)
                    .argsSchema(argsSchema.isEmpty() ? null : argsSchema)
                    .build());
        }
        return List.copyOf(out);
    }

    /**
     * 按 contributionId 在说明书 palette 中查找；找不到返回 null。
     * 内部会重新解析 palette 列表。
     */
    public static PluginPaletteNode findByContributionId(PluginManifest manifest, String contributionId) {
        return findByContributionId(listNodes(manifest), contributionId);
    }

    /**
     * 在已解析的 palette 列表中按 contributionId（即节点 id）查找。
     * 调用方已持有 listNodes 结果时用本重载，避免重复解析。
     */
    public static PluginPaletteNode findByContributionId(List<PluginPaletteNode> nodes, String contributionId) {
        if (contributionId == null || contributionId.isBlank() || nodes == null) {
            return null;
        }
        for (PluginPaletteNode n : nodes) {
            if (contributionId.equals(n.getId())) {
                return n;
            }
        }
        return null;
    }

    /** 对象转 trim 字符串；null 得空串 */
    private static String str(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /** 把 schema 对象收成 String→Object 的 Map；非 Map 或空则返回空 Map */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceSchema(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || m.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }
}
