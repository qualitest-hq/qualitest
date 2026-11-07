package com.qualitest.flow.snapshot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从流程图节点 data 读取执行前快照（checkpoint）配置。
 * <p>
 * snapshotBefore 开启时，handler 执行前先打被测数据快照，失败后可还原。
 * 保存时若写出 snapshotBefore，值须为 JSON 布尔，且仅允许挂在 http / script / assign。
 * 跑流时只认布尔 true 为开启。
 */
public final class GraphNodeSnapshotSupport {

    /** 节点 data 键名：是否在执行业务逻辑前先打被测数据快照，缺省视为 false */
    public static final String KEY_SNAPSHOT_BEFORE = "snapshotBefore";

    /** 节点 data 键名：快照范围，对象形如 { scope, tables } */
    public static final String KEY_SNAPSHOT_SCOPE = "snapshotScope";

    private GraphNodeSnapshotSupport() {
    }

    /**
     * 判断节点是否开启「执行前打被测数据快照」。
     * 只认 data.snapshotBefore 为布尔 true；缺省、false、字符串、数字一律视为未开启。
     *
     * @param node 图节点，可为 null
     * @return 是否在业务逻辑前打快照
     */
    public static boolean isSnapshotBefore(GraphNode node) {
        if (node == null || node.getData() == null) {
            return false;
        }
        return Boolean.TRUE.equals(node.getData().get(KEY_SNAPSHOT_BEFORE));
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
