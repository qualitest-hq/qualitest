package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流输入映射建议：按主入口首条场景的 inSeed 推导占位符。
 */
public final class SubflowInputSuggestSupport {

    private SubflowInputSuggestSupport() {
    }

    /**
     * 主入口节点下首条场景的 inSeed 键值（用于子流输入建议）。
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
     * 按主入口首场景初值键名建议子流输入映射。
     * 每行 name 为键名，value 为对应的流程变量占位符。
     *
     * @param graph 测试流图
     * @return 建议的输入映射行
     */
    public static List<Map<String, String>> suggestInputMappings(GraphJson graph) {
        List<Map<String, String>> out = new ArrayList<>();
        Map<String, Object> seed = primaryEntryFirstInSeed(graph);
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