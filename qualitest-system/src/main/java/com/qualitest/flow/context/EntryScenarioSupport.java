package com.qualitest.flow.context;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 scenario-entry 节点读取场景子集，供开跑解析与校验使用。
 */
public final class EntryScenarioSupport {

    private EntryScenarioSupport() {
    }

    /**
     * 定位或新建 scenario-entry；新建时写入默认场景并设置 {@code meta.startNodeId}。
     *
     * @param entryNodeId 指定入口 id；空白则取或建图上第一个入口
     */
    public static GraphNode ensureScenarioEntry(GraphJson graph, String entryNodeId) {
        GraphNode entry = findScenarioEntry(graph, entryNodeId);
        if (entry != null) {
            return entry;
        }
        entry = new GraphNode();
        entry.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
        entry.setType(FlowNodeType.SCENARIO_ENTRY.getCode());
        entry.setPosition(GraphNodePosition.builder().x(0).y(0).build());
        entry.setName("默认场景入口");

        GraphRunScenario defaultScenario = GraphRunScenario.builder()
                .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                .name("默认（冒烟）")
                .envId("")
                .inSeed(Map.of())
                .remark("")
                .build();

        Map<String, Object> data = new HashMap<>();
        data.put("doc", "");
        data.put("scenarios", List.of(defaultScenario));
        entry.setData(data);

        List<GraphNode> nodes = graph.getNodes();
        if (nodes == null) {
            nodes = new ArrayList<>();
            graph.setNodes(nodes);
        } else {
            nodes = new ArrayList<>(nodes);
            graph.setNodes(nodes);
        }
        nodes.add(0, entry);

        GraphMeta meta = graph.getMeta();
        if (meta == null) {
            meta = GraphMeta.builder().build();
            graph.setMeta(meta);
        }
        meta.setStartNodeId(entry.getId());
        return entry;
    }

    /**
     * 按 entryNodeId 定位 scenario-entry；未指定时取图上第一个场景入口。
     */
    public static GraphNode findScenarioEntry(GraphJson graph, String entryNodeId) {
        if (graph == null || graph.getNodes() == null) {
            return null;
        }
        if (entryNodeId != null && !entryNodeId.isBlank()) {
            for (GraphNode n : graph.getNodes()) {
                if (n != null && entryNodeId.equals(n.getId())
                        && FlowNodeType.SCENARIO_ENTRY.matches(n.getType())) {
                    return n;
                }
            }
            return null;
        }
        for (GraphNode n : graph.getNodes()) {
            if (n != null && FlowNodeType.SCENARIO_ENTRY.matches(n.getType())) {
                return n;
            }
        }
        return null;
    }

    /**
     * 读取入口 {@code data.scenarios}；无或非列表时返回空列表。
     */
    @SuppressWarnings("unchecked")
    public static List<GraphRunScenario> scenariosOf(GraphNode entryNode) {
        if (entryNode == null || entryNode.getData() == null) {
            return List.of();
        }
        Object raw = entryNode.getData().get("scenarios");
        if (raw == null) {
            return List.of();
        }
        List<GraphRunScenario> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                GraphRunScenario sc = coerceScenario(item);
                if (sc != null) {
                    out.add(sc);
                }
            }
            return out;
        }
        if (raw instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                GraphRunScenario sc = coerceScenario(arr.get(i));
                if (sc != null) {
                    out.add(sc);
                }
            }
            return out;
        }
        return out;
    }

    /**
     * 图上是否存在带非空 scenarios 的 scenario-entry。
     */
    public static boolean hasEntryScopedScenarios(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return false;
        }
        for (GraphNode n : graph.getNodes()) {
            if (n != null && FlowNodeType.SCENARIO_ENTRY.matches(n.getType())
                    && !scenariosOf(n).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集图上全部 scenario-entry 的场景（按节点顺序拼接；同 id 后者覆盖前者仅用于查找提示）。
     */
    public static List<GraphRunScenario> allEntryScenarios(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return List.of();
        }
        List<GraphRunScenario> all = new ArrayList<>();
        for (GraphNode n : graph.getNodes()) {
            if (n != null && FlowNodeType.SCENARIO_ENTRY.matches(n.getType())) {
                all.addAll(scenariosOf(n));
            }
        }
        return all;
    }

    private static GraphRunScenario coerceScenario(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof GraphRunScenario sc) {
            return sc;
        }
        if (item instanceof Map<?, ?> map) {
            return JSON.parseObject(JSON.toJSONString(map), GraphRunScenario.class);
        }
        if (item instanceof JSONObject jo) {
            return jo.toJavaObject(GraphRunScenario.class);
        }
        return JSON.parseObject(JSON.toJSONString(item), GraphRunScenario.class);
    }

    /**
     * 把场景列表写成入口 data 可用的普通 Map 列表（写入 envId/inSeed）。
     */
    public static List<Map<String, Object>> toEntryScenarioMaps(List<GraphRunScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraphRunScenario sc : scenarios) {
            if (sc == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sc.getId());
            m.put("name", sc.getName());
            if (sc.getEnvId() != null && !sc.getEnvId().isBlank()) {
                m.put("envId", sc.getEnvId().trim());
            }
            m.put("inSeed", sc.getInSeed() != null ? new HashMap<>(sc.getInSeed()) : Map.of());
            if (sc.getRemark() != null) {
                m.put("remark", sc.getRemark());
            }
            if (sc.getOnNodeFailure() != null) {
                m.put("onNodeFailure", sc.getOnNodeFailure());
            }
            if (sc.getOnSnapshotFailure() != null) {
                m.put("onSnapshotFailure", sc.getOnSnapshotFailure());
            }
            if (sc.getMaxSteps() != null) {
                m.put("maxSteps", sc.getMaxSteps());
            }
            out.add(m);
        }
        return out;
    }
}
