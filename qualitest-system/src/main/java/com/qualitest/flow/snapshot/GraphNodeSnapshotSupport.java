package com.qualitest.flow.snapshot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从流程图节点 data 读取 checkpoint 配置。
 */
public final class GraphNodeSnapshotSupport {

    /** 节点 data：是否在执行业务逻辑前先打数据快照，默认 false */
    public static final String KEY_SNAPSHOT_BEFORE = "snapshotBefore";

    /** 节点 data：快照范围，格式 { scope, tables } */
    public static final String KEY_SNAPSHOT_SCOPE = "snapshotScope";

    private GraphNodeSnapshotSupport() {
    }

    /**
     * 节点是否要求在 handler 执行前打 checkpoint。
     * 支持布尔或字符串 "true"/"false"；缺省为 false。
     */
    public static boolean isSnapshotBefore(GraphNode node) {
        if (node == null || node.getData() == null) {
            return false;
        }
        Object v = node.getData().get(KEY_SNAPSHOT_BEFORE);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    /**
     * 解析节点配置的快照备份范围。
     * 缺省为 scope=tables、tables 为空列表。
     */
    public static SnapshotScope resolveScope(GraphNode node) {
        if (node == null || node.getData() == null) {
            return defaultScope();
        }
        Object raw = node.getData().get(KEY_SNAPSHOT_SCOPE);
        if (raw == null) {
            return defaultScope();
        }
        JSONObject json = raw instanceof JSONObject jo
                ? jo
                : JSON.parseObject(JSON.toJSONString(raw));
        if (json == null) {
            return defaultScope();
        }
        String scope = json.getString("scope");
        if (scope == null || scope.isBlank()) {
            scope = SnapshotScope.SCOPE_TABLES;
        }
        List<String> tables = new ArrayList<>();
        if (json.getJSONArray("tables") != null) {
            for (Object t : json.getJSONArray("tables")) {
                if (t != null) {
                    String name = String.valueOf(t).trim();
                    if (!name.isEmpty()) {
                        tables.add(name);
                    }
                }
            }
        }
        return SnapshotScope.builder().scope(scope).tables(tables).build();
    }

    /** 读取节点展示名 data.name，写入快照 meta */
    public static String nodeName(GraphNode node) {
        if (node == null || node.getData() == null) {
            return "";
        }
        Object name = node.getData().get("name");
        return name != null ? String.valueOf(name) : "";
    }

    private static SnapshotScope defaultScope() {
        return SnapshotScope.builder().scope(SnapshotScope.SCOPE_TABLES).tables(List.of()).build();
    }
}
