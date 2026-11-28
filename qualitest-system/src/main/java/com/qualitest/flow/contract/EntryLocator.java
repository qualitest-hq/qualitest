package com.qualitest.flow.contract;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 从图中定位入口节点，供开跑、收束读 fallbackOut、子流调用、场景解析共用。
 * <p>
 * 规则：调用方给出的 entryNodeId 必须存在且类型符合预期；未给出时仅当图上恰好一个候选入口才采用，
 * 多个候选入口不会静默取第一个。
 */
public final class EntryLocator {

    private EntryLocator() {
    }

    /**
     * 解析开跑起点节点 id：接受任意入口类型（场景 / HTTP / 子流入口）。
     * 未指定且存在多个入口时抛错。
     */
    public static String requireStartId(GraphJson graph, String entryNodeId) {
        return requireEntryId(graph, entryNodeId, FlowNodeType::isEntry, FlowErrorCode.TF_START_NODE);
    }

    /**
     * 可选定位任意入口节点（例如收束时读 fallbackOut）。
     * 显式 id 类型不符、多入口未指定、图上无入口时返回 empty。
     */
    public static Optional<GraphNode> findOptional(GraphJson graph, String entryNodeId) {
        return findOptionalMatching(graph, entryNodeId, FlowNodeType::isEntry);
    }

    /**
     * 解析子流调用入口 id：只接受类型为 subflow-entry 的节点。
     * <ul>
     *   <li>指定了 id：必须存在且类型为 subflow-entry</li>
     *   <li>未指定：图上恰好一个 subflow-entry 时用之；否则抛错并说明原因</li>
     * </ul>
     */
    public static String requireSubflowEntryId(GraphJson graph, String entryNodeId) {
        if (graph == null || graph.getNodes() == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "子流图为空");
        }
        String specified = blankToNull(entryNodeId);
        if (specified != null) {
            GraphNode found = findById(graph, specified).orElse(null);
            if (found == null) {
                throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID,
                        "子流入口不存在: " + specified);
            }
            if (!FlowNodeType.SUBFLOW_ENTRY.matches(found.getType())) {
                throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID,
                        "指定入口须为 subflow-entry: " + specified);
            }
            return specified;
        }
        List<GraphNode> subEntries = listMatching(graph, t -> FlowNodeType.SUBFLOW_ENTRY.matches(t));
        if (subEntries.size() == 1) {
            return subEntries.get(0).getId();
        }
        if (subEntries.size() > 1) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID,
                    "子流存在多个子流入口，须在父节点指定 entryNodeId");
        }
        List<GraphNode> anyEntries = listMatching(graph, FlowNodeType::isEntry);
        if (anyEntries.size() > 1) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID,
                    "子流存在多个入口且无 subflow-entry，须指定 entryNodeId");
        }
        if (anyEntries.size() == 1) {
            throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID,
                    "子流唯一入口须为 subflow-entry");
        }
        throw new FlowExecutionException(FlowErrorCode.TF_SUBFLOW_INVALID, "子流缺少入口节点");
    }

    /**
     * 定位场景入口节点：只接受类型为 scenario-entry。
     * 多入口未指定或找不到时返回 empty。
     */
    public static Optional<GraphNode> findScenarioEntry(GraphJson graph, String entryNodeId) {
        return findOptionalMatching(graph, entryNodeId, t -> FlowNodeType.SCENARIO_ENTRY.matches(t));
    }

    /** 强制解析入口 id；失败时抛出带业务码的执行异常。 */
    private static String requireEntryId(
            GraphJson graph,
            String entryNodeId,
            Predicate<String> typeOk,
            FlowErrorCode code) {
        if (graph == null || graph.getNodes() == null) {
            throw new FlowExecutionException(code, "未找到开始节点");
        }
        String specified = blankToNull(entryNodeId);
        if (specified != null) {
            GraphNode start = findById(graph, specified).orElse(null);
            if (start == null) {
                throw new FlowExecutionException(code, "入口节点不存在: " + specified);
            }
            if (!typeOk.test(start.getType())) {
                throw new FlowExecutionException(code,
                        "entryNodeId 须为入口节点，当前类型: " + start.getType());
            }
            return specified;
        }
        List<GraphNode> entries = listMatching(graph, typeOk);
        if (entries.size() == 1) {
            String id = entries.get(0).getId();
            if (id == null || id.isBlank()) {
                throw new FlowExecutionException(code, "未找到开始节点");
            }
            return id;
        }
        if (entries.size() > 1) {
            List<String> ids = new ArrayList<>();
            for (GraphNode n : entries) {
                if (n.getId() != null && !n.getId().isBlank()) {
                    ids.add(n.getId());
                }
            }
            ids.sort(String::compareTo);
            throw new FlowExecutionException(code,
                    "存在多个入口，须指定 entryNodeId: " + String.join(", ", ids));
        }
        throw new FlowExecutionException(code, "须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑");
    }

    /** 可选解析：显式 id 或唯一候选；否则 empty。 */
    private static Optional<GraphNode> findOptionalMatching(
            GraphJson graph, String entryNodeId, Predicate<String> typeOk) {
        if (graph == null || graph.getNodes() == null) {
            return Optional.empty();
        }
        String specified = blankToNull(entryNodeId);
        if (specified != null) {
            return findById(graph, specified).filter(n -> typeOk.test(n.getType()));
        }
        List<GraphNode> entries = listMatching(graph, typeOk);
        return entries.size() == 1 ? Optional.of(entries.get(0)) : Optional.empty();
    }

    /** 按节点 id 查找。 */
    private static Optional<GraphNode> findById(GraphJson graph, String id) {
        if (graph == null || graph.getNodes() == null || id == null) {
            return Optional.empty();
        }
        for (GraphNode n : graph.getNodes()) {
            if (n != null && id.equals(n.getId())) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    /** 列出类型满足谓词、且 id 非空的节点。 */
    private static List<GraphNode> listMatching(GraphJson graph, Predicate<String> typeOk) {
        List<GraphNode> out = new ArrayList<>();
        if (graph == null || graph.getNodes() == null) {
            return out;
        }
        for (GraphNode n : graph.getNodes()) {
            if (n == null || n.getId() == null || n.getId().isBlank()) {
                continue;
            }
            if (typeOk.test(n.getType())) {
                out.add(n);
            }
        }
        return out;
    }

    /** 空白字符串视为未指定。 */
    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
