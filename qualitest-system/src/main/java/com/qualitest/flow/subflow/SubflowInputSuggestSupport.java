package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流输入映射建议：优先子流入口 {@code inputDecl} 的扁平键名；没有子流入口的旧图
 * 回落到唯一场景入口首条场景的 inSeed 键名。
 */
public final class SubflowInputSuggestSupport {

    private SubflowInputSuggestSupport() {
    }

    /**
     * 唯一子流入口 {@code data.inputDecl} 的键名（扁平 {@code [{name,type,required}]}）。
     * 无子流入口、多个子流入口（无法唯一解析）或声明为空时返回空列表。
     */
    public static List<String> primarySubflowInputDeclNames(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return List.of();
        }
        GraphNode sole = null;
        for (GraphNode n : graph.getNodes()) {
            if (n == null || !FlowNodeType.SUBFLOW_ENTRY.matches(n.getType())) {
                continue;
            }
            if (sole != null) {
                return List.of();
            }
            sole = n;
        }
        if (sole == null || sole.getData() == null) {
            return List.of();
        }
        Object raw = sole.getData().get("inputDecl");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object nameObj = row.get("name");
            String key = nameObj == null ? "" : String.valueOf(nameObj).trim();
            if (!key.isEmpty() && !names.contains(key)) {
                names.add(key);
            }
        }
        return names;
    }

    /**
     * 唯一场景入口下首条场景的 inSeed 键值（用于子流输入建议）；多入口无法唯一解析时为空。
     */
    public static Map<String, Object> primaryEntryFirstInSeed(GraphJson graph) {
        GraphNode entry = EntryScenarioSupport.findScenarioEntry(graph, null);
        if (entry == null) {
            return Map.of();
        }
        List<GraphRunScenario> scenarios = EntryScenarioSupport.scenariosOf(entry);
        if (scenarios.isEmpty()) {
            return Map.of();
        }
        GraphRunScenario first = scenarios.get(0);
        Map<String, Object> inSeed = first.getInSeed();
        if (inSeed == null || inSeed.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(inSeed);
    }

    /**
     * 建议子流输入映射：优先按子流入口 {@code inputDecl} 的键名；没有可用声明时
     * 回落到主（场景）入口首场景的 inSeed 键名。每行 name 为键名，value 为对应流程变量占位符。
     *
     * @param graph 测试流图
     * @return 建议的输入映射行
     */
    public static List<Map<String, String>> suggestInputMappings(GraphJson graph) {
        List<Map<String, String>> out = new ArrayList<>();
        List<String> declared = primarySubflowInputDeclNames(graph);
        Iterable<String> keys = declared.isEmpty()
                ? primaryEntryFirstInSeed(graph).keySet()
                : declared;
        for (String key : keys) {
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
     * 将建议的输入映射编成 JSON 数组。
     * 每项含 name 与 value。
     *
     * @param graph 测试流图
     * @return JSON 数组
     */
    public static JSONArray suggestInputMappingsJson(GraphJson graph) {
        JSONArray out = new JSONArray();
        for (Map<String, String> row : suggestInputMappings(graph)) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("name", row.get("name"));
            item.put("value", row.get("value"));
            out.add(item);
        }
        return out;
    }

}