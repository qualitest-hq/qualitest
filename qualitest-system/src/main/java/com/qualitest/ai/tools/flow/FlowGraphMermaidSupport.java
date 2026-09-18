package com.qualitest.ai.tools.flow;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将测试流 {@link GraphJson} 转为 Mermaid {@code flowchart TB} 正文（不含 markdown 围栏）。
 * <p>
 * 用于只读拓扑预览：节点含类型与名称，边含可选分支标签。
 */
public final class FlowGraphMermaidSupport {

    private FlowGraphMermaidSupport() {
    }

    /**
     * 将整图转为 Mermaid flowchart 正文。
     *
     * @param graph 测试流图，可为 null
     * @return flowchart TB 文本；空图时仅含头行
     */
    public static String toMermaid(GraphJson graph) {
        return toMermaid(graph, null);
    }

    /**
     * 将图转为 Mermaid flowchart 正文；{@code includeNodeIds} 非空时只输出这些节点及两端均在集合内的边。
     *
     * @param graph          测试流图，可为 null
     * @param includeNodeIds 子集节点 id；null 或空表示全图
     * @return flowchart TB 文本
     */
    public static String toMermaid(GraphJson graph, Collection<String> includeNodeIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TB");
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return sb.toString();
        }
        Set<String> include = null;
        if (includeNodeIds != null && !includeNodeIds.isEmpty()) {
            include = new HashSet<>();
            for (String id : includeNodeIds) {
                if (id != null && !id.isBlank()) {
                    include.add(id.trim());
                }
            }
            if (include.isEmpty()) {
                return sb.toString();
            }
        }
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            String id = node.getId().trim();
            if (include != null && !include.contains(id)) {
                continue;
            }
            byId.put(id, node);
            sb.append('\n').append("  ").append(formatNodeDeclaration(node));
        }
        if (byId.isEmpty()) {
            return sb.toString();
        }
        List<GraphEdge> edges = graph.getEdges();
        if (edges == null) {
            return sb.toString();
        }
        for (GraphEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String source = edge.getSource() != null ? edge.getSource().trim() : "";
            String target = edge.getTarget() != null ? edge.getTarget().trim() : "";
            if (source.isEmpty() || target.isEmpty()) {
                continue;
            }
            if (!byId.containsKey(source) || !byId.containsKey(target)) {
                continue;
            }
            String label = resolveEdgeLabel(edge, byId.get(source), target);
            sb.append('\n').append("  ").append(safeId(source));
            if (label != null && !label.isBlank()) {
                sb.append(" -->|").append(escapeEdgeLabel(label)).append("| ");
            } else {
                sb.append(" --> ");
            }
            sb.append(safeId(target));
        }
        return sb.toString();
    }

    /**
     * 生成单个节点声明行（含形状）。
     */
    static String formatNodeDeclaration(GraphNode node) {
        String id = safeId(node.getId().trim());
        String label = nodeLabel(node);
        String escaped = escapeNodeLabel(label);
        if (FlowNodeType.CONDITION.matches(node.getType())) {
            return id + "{\"" + escaped + "\"}";
        }
        if (FlowNodeType.SUBFLOW.matches(node.getType())) {
            return id + "[[\"" + escaped + "\"]]";
        }
        return id + "[\"" + escaped + "\"]";
    }

    /**
     * 节点展示标签：类型标签 · name（空则用 summary）。
     */
    static String nodeLabel(GraphNode node) {
        String typeLabel = FlowNodeType.labelOf(node.getType());
        if (typeLabel == null || typeLabel.isBlank()) {
            typeLabel = node.getType() != null ? node.getType() : "?";
        }
        String name = "";
        String summary = "";
        if (node.getData() != null) {
            Object n = node.getData().get("name");
            if (n != null && !String.valueOf(n).isBlank()) {
                name = String.valueOf(n).trim();
            }
            Object s = node.getData().get("summary");
            if (s != null && !String.valueOf(s).isBlank()) {
                summary = String.valueOf(s).trim();
            }
        }
        String detail = !name.isEmpty() ? name : summary;
        if (detail.isEmpty()) {
            return typeLabel;
        }
        return typeLabel + " · " + detail;
    }

    /**
     * 边标签：优先 edge.label；源为 condition 时从 branches 按 target 取 name，再退回 kind。
     */
    static String resolveEdgeLabel(GraphEdge edge, GraphNode sourceNode, String targetId) {
        if (edge.getLabel() != null && !edge.getLabel().isBlank()) {
            return edge.getLabel().trim();
        }
        if (sourceNode == null || !FlowNodeType.CONDITION.matches(sourceNode.getType())) {
            return null;
        }
        Map<String, Object> data = sourceNode.getData();
        if (data == null) {
            return null;
        }
        Object branchesObj = data.get("branches");
        if (!(branchesObj instanceof List<?> branches)) {
            return null;
        }
        for (Object item : branches) {
            if (!(item instanceof Map<?, ?> branch)) {
                continue;
            }
            Object target = branch.get("target");
            if (target == null || !targetId.equals(String.valueOf(target).trim())) {
                continue;
            }
            Object name = branch.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name).trim();
            }
            Object kind = branch.get("kind");
            if (kind != null && !String.valueOf(kind).isBlank()) {
                return String.valueOf(kind).trim();
            }
            Object id = branch.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id).trim();
            }
        }
        return null;
    }

    /**
     * Mermaid 节点 id：非字母数字下划线改为下划线；空则 n。
     */
    static String safeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "n";
        }
        String s = raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) {
            return "n";
        }
        if (Character.isDigit(s.charAt(0))) {
            return "n" + s;
        }
        return s;
    }

    /**
     * 节点标签转义：去掉会破坏形状的引号与括号类字符。
     */
    static String escapeNodeLabel(String label) {
        if (label == null) {
            return "";
        }
        return label
                .replace("\"", "'")
                .replace("[", "(")
                .replace("]", ")")
                .replace("{", "(")
                .replace("}", ")")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /**
     * 边标签转义：去掉管道与换行。
     */
    static String escapeEdgeLabel(String label) {
        if (label == null) {
            return "";
        }
        return label
                .replace("|", "/")
                .replace("\n", " ")
                .replace("\r", "")
                .replace("\"", "'");
    }
}
