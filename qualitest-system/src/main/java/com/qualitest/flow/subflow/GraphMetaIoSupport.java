package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试流图元数据上的子流输入输出辅助。
 * <p>
 * 负责：规范化与解析图级对外产出声明、生成子流节点默认输出映射、
 * 按激活场景初值建议输入映射，以及把上述结构编成 JSON 数组供工具回执。
 */
public final class GraphMetaIoSupport {

    private GraphMetaIoSupport() {
    }

    /**
     * 规范化对外产出声明列表。
     * 去掉空项与空白名称，裁剪名称与说明两端空白；入参为 null 时返回空列表。
     *
     * @param raw 原始产出声明
     * @return 规范化后的新列表
     */
    public static List<GraphFlowOutput> normalizeFlowOutputs(List<GraphFlowOutput> raw) {
        List<GraphFlowOutput> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (GraphFlowOutput item : raw) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            String description = item.getDescription() != null ? item.getDescription().trim() : null;
            if (description != null && description.isEmpty()) {
                description = null;
            }
            out.add(GraphFlowOutput.builder()
                    .name(item.getName().trim())
                    .description(description)
                    .build());
        }
        return out;
    }

    /**
     * 从工具入参解析对外产出声明。
     * 入参须为数组，元素为含 name 的对象，可选 description；非法类型抛出异常。
     *
     * @param raw 工具参数中的 flowOutputs 原值
     * @return 规范化后的产出声明
     * @throws IllegalArgumentException 不是数组或元素不是对象时
     */
    public static List<GraphFlowOutput> parseFlowOutputsArg(Object raw) {
        if (!(raw instanceof List<?> rows)) {
            throw new IllegalArgumentException("flowOutputs 须为数组");
        }
        List<GraphFlowOutput> parsed = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("flowOutputs 每项须为对象");
            }
            Object nameObj = map.get("name");
            String name = nameObj != null ? String.valueOf(nameObj).trim() : "";
            if (name.isEmpty()) {
                continue;
            }
            Object descObj = map.get("description");
            String description = descObj != null ? String.valueOf(descObj).trim() : null;
            if (description != null && description.isEmpty()) {
                description = null;
            }
            parsed.add(GraphFlowOutput.builder().name(name).description(description).build());
        }
        return normalizeFlowOutputs(parsed);
    }

    /**
     * 按图元数据中的对外产出声明生成默认输出映射。
     * 每一项为 name 与 flowKey 相同的一对，供子流节点未配置 outputs 时写回父流程变量。
     *
     * @param meta 图元数据，可为 null
     * @return 默认输出映射行列表
     */
    public static List<Map<String, String>> defaultOutputMappings(GraphMeta meta) {
        List<Map<String, String>> out = new ArrayList<>();
        for (GraphFlowOutput item : normalizeFlowOutputs(meta == null ? null : meta.getFlowOutputs())) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", item.getName());
            row.put("flowKey", item.getName());
            out.add(row);
        }
        return out;
    }

    /**
     * 读取当前激活运行场景的流程变量初值。
     * 无场景或初值为空时返回空 Map。
     *
     * @param meta 图元数据
     * @return 初值副本
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
     * 按激活场景初值键名建议子流输入映射。
     * 每行 name 为键名，value 为对应的流程变量占位符。
     *
     * @param meta 图元数据
     * @return 建议的输入映射行
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
     * 提取对外产出声明的名称列表。
     *
     * @param meta 图元数据
     * @return 名称列表
     */
    public static List<String> flowOutputNames(GraphMeta meta) {
        List<String> names = new ArrayList<>();
        for (GraphFlowOutput item : normalizeFlowOutputs(meta == null ? null : meta.getFlowOutputs())) {
            names.add(item.getName());
        }
        return names;
    }

    /**
     * 将对外产出声明列表编成 JSON 数组。
     * 每项含 name，有说明时含 description。
     *
     * @param outputs 产出声明列表
     * @return JSON 数组
     */
    public static JSONArray flowOutputsJson(List<GraphFlowOutput> outputs) {
        JSONArray out = new JSONArray();
        for (GraphFlowOutput item : normalizeFlowOutputs(outputs)) {
            JSONObject row = new JSONObject();
            row.put("name", item.getName());
            if (item.getDescription() != null && !item.getDescription().isBlank()) {
                row.put("description", item.getDescription());
            }
            out.add(row);
        }
        return out;
    }

    /**
     * 将图元数据中的对外产出声明编成 JSON 数组。
     *
     * @param meta 图元数据
     * @return JSON 数组
     */
    public static JSONArray flowOutputsJson(GraphMeta meta) {
        return flowOutputsJson(meta == null ? null : meta.getFlowOutputs());
    }

    /**
     * 将建议的输入映射编成 JSON 数组。
     * 每项含 name 与 value。
     *
     * @param meta 图元数据
     * @return JSON 数组
     */
    public static JSONArray suggestInputMappingsJson(GraphMeta meta) {
        JSONArray out = new JSONArray();
        for (Map<String, String> row : suggestInputMappings(meta)) {
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

    /**
     * 按场景 id 在列表中查找场景。
     *
     * @param scenarios 场景列表
     * @param id        场景 id
     * @return 匹配场景，未找到则 null
     */
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
