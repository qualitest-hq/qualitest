package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 测试流 graph_json 的稳定哈希工具。
 *
 * 用于 AI patch 试算时记录「试算发起时刻的画布指纹」，合并前比对当前画布是否被手改。
 * 规则：节点/边按 id 排序后序列化（Map 字段排序），SHA-256 取前 16 位 hex。
 */
public final class GraphJsonHashUtil {

    private static final int HASH_PREFIX_LENGTH = 16;

    private GraphJsonHashUtil() {
    }

    /**
     * 计算 graph_json 的稳定哈希前缀。
     *
     * @param graph 当前画布 graph_json；null 视为空图
     * @return 16 位 hex 前缀；计算失败时返回空字符串
     */
    public static String computeBaseGraphHash(GraphJson graph) {
        if (graph == null) {
            graph = GraphJson.builder().build();
        }
        GraphJson canonical = canonicalize(graph);
        String json = JSON.toJSONString(canonical, JSONWriter.Feature.SortMapEntriesByKeys);
        return sha256Prefix(json);
    }

    static GraphJson canonicalize(GraphJson graph) {
        GraphJson copy = JSON.parseObject(JSON.toJSONString(graph), GraphJson.class);
        if (copy.getNodes() != null) {
            List<GraphNode> nodes = new ArrayList<>(copy.getNodes());
            nodes.sort(Comparator.comparing(n -> n.getId() == null ? "" : n.getId()));
            copy.setNodes(nodes);
        }
        if (copy.getEdges() != null) {
            List<GraphEdge> edges = new ArrayList<>(copy.getEdges());
            edges.sort(Comparator.comparing(e -> e.getId() == null ? "" : e.getId()));
            copy.setEdges(edges);
        }
        return copy;
    }

    static String sha256Prefix(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return hex.length() <= HASH_PREFIX_LENGTH ? hex : hex.substring(0, HASH_PREFIX_LENGTH);
        } catch (Exception e) {
            return "";
        }
    }
}
