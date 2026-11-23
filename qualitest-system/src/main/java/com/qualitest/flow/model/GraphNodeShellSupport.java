package com.qualitest.flow.model;

import com.alibaba.fastjson2.JSONObject;

import com.qualitest.flow.validate.FlowNodeType;

/**
 * 节点壳字段约定：展示名写在节点顶层 {@code name}；{@code data} 仅存该 type 业务配置。
 */
public final class GraphNodeShellSupport {

    private GraphNodeShellSupport() {
    }

    /** 顶层 name 与 id 回退。 */
    public static String displayNameFromParts(String topLevelName, String id) {
        if (topLevelName != null && !topLevelName.isBlank()) {
            return topLevelName.trim();
        }
        if (id != null && !id.isBlank()) {
            return id;
        }
        return "(未知)";
    }

    /**
     * 解析节点展示名：顶壳 {@code name}；空白时回落类型标签。
     */
    public static String resolveNodeName(GraphNode node) {
        if (node == null) {
            return "";
        }
        if (node.getName() != null) {
            String text = node.getName().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return FlowNodeType.labelOf(node.getType());
    }

    /** 节点展示名：顶层 name，否则类型标签；仍空则「(未知)」。 */
    public static String displayName(GraphNode node) {
        if (node == null) {
            return "(未知)";
        }
        String resolved = resolveNodeName(node);
        return resolved.isEmpty() ? "(未知)" : resolved;
    }

    /** 原始 JSON 节点对象的展示名。 */
    public static String displayName(JSONObject node) {
        if (node == null) {
            return "(未知)";
        }
        String name = node.getString("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String type = node.getString("type");
        if (type != null && !type.isBlank()) {
            String label = FlowNodeType.labelOf(type);
            if (label != null && !label.isEmpty()) {
                return label;
            }
        }
        String id = node.getString("id");
        if (id != null && !id.isBlank()) {
            return id;
        }
        return "(未知)";
    }
}