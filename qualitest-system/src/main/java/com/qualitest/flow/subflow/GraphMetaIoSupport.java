package com.qualitest.flow.subflow;

import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从画布 {@link GraphMeta} 解析子流 IO 契约与运行场景辅助信息。
 * <p>
 * {@code meta.flowOutputs} 声明子图对外产出变量名；subflow 节点 outputs 为空时，
 * {@link #defaultOutputMappings} 生成 {@code name → flowKey} 的一一映射。
 * {@link #flowOutputNames} 供 AI 工具与校验提示使用。
 */
public final class GraphMetaIoSupport {

    private GraphMetaIoSupport() {
    }

    /**
     * 从 {@code meta.flowOutputs} 生成默认 outputs 映射：{@code {name, flowKey:name}}。
     */
    public static List<Map<String, String>> defaultOutputMappings(GraphMeta meta) {
        List<Map<String, String>> out = new ArrayList<>();
        if (meta == null || meta.getFlowOutputs() == null) {
            return out;
        }
        for (GraphFlowOutput item : meta.getFlowOutputs()) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            String name = item.getName().trim();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("flowKey", name);
            out.add(row);
        }
        return out;
    }

    /**
     * 按 {@code activeScenarioId} 取当前激活场景的 {@code flowSeed}。
     */
    public static Map<String, Object> activeScenarioFlowSeed(GraphMeta meta) {
        if (meta == null || meta.getScenarios() == null || meta.getScenarios().isEmpty()) {
            return Map.of();
        }
        String activeId = meta.getActiveScenarioId();
        if (activeId == null || activeId.isBlank()) {
            activeId = meta.getScenarios().get(0).getId();
        }
        GraphRunScenario matched = findScenario(meta.getScenarios(), activeId);
        if (matched == null) {
            matched = meta.getScenarios().get(0);
        }
        if (matched.getFlowSeed() == null || matched.getFlowSeed().isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(matched.getFlowSeed());
    }

    /**
     * 按 active 场景 {@code flowSeed} 键名建议 inputs 映射：{@code {name, value:"{{flow.key}}"}}。
     */
    public static List<Map<String, String>> suggestInputMappings(GraphMeta meta) {
        List<Map<String, String>> out = new ArrayList<>();
        Map<String, Object> seed = activeScenarioFlowSeed(meta);
        for (String key : seed.keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", key);
            row.put("value", "{{flow." + key + "}}");
            out.add(row);
        }
        return out;
    }

    /**
     * 提取 flowOutputs 名称列表。
     */
    public static List<String> flowOutputNames(GraphMeta meta) {
        List<String> names = new ArrayList<>();
        if (meta == null || meta.getFlowOutputs() == null) {
            return names;
        }
        for (GraphFlowOutput item : meta.getFlowOutputs()) {
            if (item != null && item.getName() != null && !item.getName().isBlank()) {
                names.add(item.getName().trim());
            }
        }
        return names;
    }

    private static GraphRunScenario findScenario(List<GraphRunScenario> scenarios, String id) {
        if (scenarios == null || id == null) {
            return null;
        }
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null && id.equals(scenario.getId())) {
                return scenario;
            }
        }
        return null;
    }
}
