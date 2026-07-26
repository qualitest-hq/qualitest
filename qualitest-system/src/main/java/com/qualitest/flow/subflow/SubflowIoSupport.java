package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流节点与父流程之间的 inputs/outputs 变量传递。
 * <p>
 * 节点 data 约定：
 * <ul>
 *   <li>{@code inputs} — {@code [{name, value}]}，value 支持 {@code {{flow.*}}}/{@code {{asset.*}}}/{@code {{env.*}}}，
 *       strict 解析后写入子上下文 flow 作为种子</li>
 *   <li>{@code outputs} — {@code [{name, flowKey}]}，从子 flow 的 {@code name} 取值，写回父 flow 的 {@code flowKey}；
 *       为空时使用子图 {@code meta.flowOutputs} 默认映射（见 {@link GraphMetaIoSupport}）</li>
 * </ul>
 */
public final class SubflowIoSupport {

    private static final PlaceholderResolver STRICT = PlaceholderResolver.strict();

    private SubflowIoSupport() {
    }

    /**
     * 根据节点 inputs 配置，从父上下文解析占位符，得到子图启动时的 flow 种子。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveInputSeed(FlowRunContext parentCtx, Object inputsObj) {
        Map<String, Object> seed = new HashMap<>();
        if (!(inputsObj instanceof List<?> rows)) {
            return seed;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String name = map.get("name") != null ? String.valueOf(map.get("name")).trim() : "";
            if (name.isEmpty()) {
                continue;
            }
            Object valueObj = map.get("value");
            String template = valueObj != null ? String.valueOf(valueObj) : "";
            String resolved = STRICT.resolve(template, parentCtx);
            seed.put(name, coerceValue(resolved));
        }
        return seed;
    }

    /**
     * 子图执行成功后，按 outputs 映射把子 flow 变量合并进父 flow，并返回本次写入的键值副本。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyOutputs(
            FlowRunContext parentCtx,
            Map<String, Object> childFlow,
            Object outputsObj
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (!(outputsObj instanceof List<?> rows) || childFlow == null) {
            return merged;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String name = map.get("name") != null ? String.valueOf(map.get("name")).trim() : "";
            String flowKey = map.get("flowKey") != null ? String.valueOf(map.get("flowKey")).trim() : name;
            if (name.isEmpty() || flowKey.isEmpty()) {
                continue;
            }
            if (childFlow.containsKey(name)) {
                Object val = childFlow.get(name);
                parentCtx.getFlow().put(flowKey, val);
                merged.put(flowKey, val);
            }
        }
        return merged;
    }

    /** 判断 inputs/outputs 映射列表是否为空或未配置 */
    public static boolean isEmptyMappingList(Object listObj) {
        if (!(listObj instanceof List<?> rows)) {
            return true;
        }
        return rows.isEmpty();
    }

    /**
     * 将子图内存执行产生的 {@link com.qualitest.flow.node.StepResult} 列表压缩为可落库/展示的 childSteps 摘要。
     */
    public static List<Map<String, Object>> toChildStepSummaries(List<com.qualitest.flow.node.StepResult> steps) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (steps == null) {
            return out;
        }
        for (com.qualitest.flow.node.StepResult step : steps) {
            if (step == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nodeId", step.getNodeId());
            row.put("nodeType", step.getNodeType());
            row.put("nodeName", step.getNodeName());
            row.put("status", step.getStatus());
            row.put("durationMs", step.getDurationMs());
            if (step.getError() != null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", step.getError().getCode());
                err.put("message", step.getError().getMessage());
                row.put("error", err);
            }
            out.add(row);
        }
        return out;
    }

    /** 占位符解析后的字符串尝试转为 boolean / 数字 / JSON，失败则保留原字符串 */
    private static Object coerceValue(String resolved) {
        if (resolved == null) {
            return "";
        }
        String trimmed = resolved.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JSONObject.parse(trimmed);
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }
}
