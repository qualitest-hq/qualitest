package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.project.domain.TestProjectApi;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 测试流 HTTP 节点测值覆盖字段 {@code requestValueOverrides} 的读写辅助。
 * <p>
 * 节点只存测值，不存完整请求结构。正式形状两桶：
 * <ul>
 *   <li>{@code paramDefaults}：按参数名覆盖 query / path / header / form-data / urlencoded 的 value</li>
 *   <li>{@code bodyExample}：覆盖 JSON body 测值</li>
 * </ul>
 * 用途：相对资产默认值做差分、写成可落盘的 Map。
 * <p>
 * Agent 偶发把字段写在 overrides 顶层：若名属于接口参数结构则归入 paramDefaults，
 * 否则归入 bodyExample；已误放进 bodyExample 的参数名也会迁回 paramDefaults。
 */
public final class HttpNodeRequestValueOverridesSupport {

    /** 合法的 overrides 顶层键；其余为待分桶的杂项 */
    private static final Set<String> KNOWN_OVERRIDE_KEYS = Set.of("paramDefaults", "bodyExample");

    /** 请求结构里带 name 的参数数组字段 */
    private static final String[] REQUEST_PARAM_ARRAY_FIELDS = {
            "queryParams", "pathParams", "declaredHeaders"
    };

    private HttpNodeRequestValueOverridesSupport() {
    }

    /**
     * 汇总节点上已有 overrides 与临时 requestBody，
     * 再与 API 资产 test_value_config 中的默认测值比对，只留下不同的项。
     * 全部相同则返回空对象。
     */
    public static JSONObject buildDiffOverrides(
            Object existingOverridesRaw,
            Object requestBodyRaw,
            TestProjectApi api) {
        Set<String> knownParams = collectParamNamesFromApi(api);
        JSONObject merged = new JSONObject();
        JSONObject existing = toJsonObject(existingOverridesRaw);
        if (existing != null && !existing.isEmpty()) {
            // 保留顶层杂项，交给后续按接口参数名分桶
            merged.putAll(existing);
        }
        applyRequestBodyAsBodyExample(merged, requestBodyRaw);
        normalizeOverridesShapeInPlace(merged, knownParams);

        JSONObject assetDefaults = assetRequestValueDefaults(api);
        return diffAgainstDefaults(merged, assetDefaults);
    }

    /**
     * 纠正 overrides 形状（无接口参数上下文）：顶层杂项一律抬进 bodyExample。
     * 有接口结构时应使用 {@link #normalizeOverridesShape(Object, Collection)}。
     */
    public static JSONObject normalizeOverridesShape(Object raw) {
        return normalizeOverridesShape(raw, Collections.emptySet());
    }

    /**
     * 纠正 overrides 形状：按接口参数名分桶。
     * <ul>
     *   <li>顶层杂项名 ∈ knownParamNames → paramDefaults</li>
     *   <li>其余顶层杂项 → bodyExample</li>
     *   <li>bodyExample 对象内命中参数名的键 → 迁回 paramDefaults</li>
     * </ul>
     *
     * @param raw             overrides 原始值
     * @param knownParamNames 接口 query/path/header/form 参数名；可空
     * @return 纠正后的新对象；raw 无法解析则 null
     */
    public static JSONObject normalizeOverridesShape(Object raw, Collection<String> knownParamNames) {
        JSONObject source = toJsonObject(raw);
        if (source == null) {
            return null;
        }
        JSONObject copy = new JSONObject(source);
        normalizeOverridesShapeInPlace(copy, knownParamNames);
        return copy;
    }

    /**
     * 就地按接口参数名分桶。
     *
     * @param overrides       待纠正对象
     * @param knownParamNames 接口参数名集合；可空
     */
    static void normalizeOverridesShapeInPlace(JSONObject overrides, Collection<String> knownParamNames) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Set<String> known = knownParamNames == null || knownParamNames.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(knownParamNames);

        JSONObject strayParams = new JSONObject();
        JSONObject strayBody = new JSONObject();
        for (String key : List.copyOf(overrides.keySet())) {
            if (KNOWN_OVERRIDE_KEYS.contains(key)) {
                continue;
            }
            Object value = overrides.remove(key);
            if (known.contains(key)) {
                strayParams.put(key, value);
            } else {
                strayBody.put(key, value);
            }
        }
        if (!strayParams.isEmpty()) {
            ensureParamDefaults(overrides).putAll(strayParams);
        }
        if (!strayBody.isEmpty()) {
            mergeIntoBodyExample(overrides, strayBody);
        }
        reclaimParamNamesFromBodyExample(overrides, known);
    }

    /**
     * 从接口 requestConfig 收集可走 paramDefaults 的参数名
     * （query / path / header / form-data / urlencoded）。
     *
     * @param api 项目接口；可空
     * @return 参数名集合；无结构时为空集
     */
    public static Set<String> collectParamNamesFromApi(TestProjectApi api) {
        if (api == null) {
            return Collections.emptySet();
        }
        return collectParamNames(api.getRequestConfig());
    }

    /**
     * 从请求结构 JSON 收集参数名。
     *
     * @param requestConfigJson request_config 文本；可空
     * @return 参数名集合
     */
    public static Set<String> collectParamNames(String requestConfigJson) {
        if (requestConfigJson == null || requestConfigJson.isBlank()) {
            return Collections.emptySet();
        }
        try {
            JSONObject root = JSON.parseObject(requestConfigJson);
            if (root == null) {
                return Collections.emptySet();
            }
            Set<String> names = new LinkedHashSet<>();
            for (String field : REQUEST_PARAM_ARRAY_FIELDS) {
                collectNamesFromParamArray(root.getJSONArray(field), names);
            }
            JSONObject body = root.getJSONObject("body");
            if (body != null) {
                collectNamesFromParamArray(body.getJSONArray("formData"), names);
                collectNamesFromParamArray(body.getJSONArray("urlencoded"), names);
            }
            return names;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /** 遍历参数数组，收集非空 name。 */
    private static void collectNamesFromParamArray(JSONArray arr, Set<String> names) {
        if (arr == null || arr.isEmpty()) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String name = row.getString("name");
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
    }

    /** 确保 paramDefaults 对象存在并返回。 */
    private static JSONObject ensureParamDefaults(JSONObject overrides) {
        JSONObject params = overrides.getJSONObject("paramDefaults");
        if (params == null) {
            params = new JSONObject();
            overrides.put("paramDefaults", params);
        }
        return params;
    }

    /** 把 stray 合并进 bodyExample（对象则合并；缺失则整段写入；非对象则丢弃 stray）。 */
    private static void mergeIntoBodyExample(JSONObject overrides, JSONObject stray) {
        if (stray == null || stray.isEmpty()) {
            return;
        }
        Object existingBody = overrides.get("bodyExample");
        if (existingBody instanceof Map<?, ?> map) {
            JSONObject bodyObj = existingBody instanceof JSONObject jo ? jo : new JSONObject(map);
            bodyObj.putAll(stray);
            overrides.put("bodyExample", bodyObj);
            return;
        }
        if (existingBody == null) {
            overrides.put("bodyExample", stray);
        }
        // bodyExample 已是字符串等非对象：保留原值，丢弃 stray
    }

    /**
     * 把 bodyExample 对象里命中接口参数名的键迁回 paramDefaults（纠正历史误落盘）。
     */
    private static void reclaimParamNamesFromBodyExample(JSONObject overrides, Set<String> known) {
        if (known == null || known.isEmpty()) {
            return;
        }
        Object bodyRaw = overrides.get("bodyExample");
        if (!(bodyRaw instanceof Map<?, ?>)) {
            return;
        }
        JSONObject bodyObj = bodyRaw instanceof JSONObject jo ? jo : new JSONObject((Map<?, ?>) bodyRaw);
        JSONObject moved = new JSONObject();
        for (String key : List.copyOf(bodyObj.keySet())) {
            if (key != null && known.contains(key)) {
                moved.put(key, bodyObj.remove(key));
            }
        }
        if (!moved.isEmpty()) {
            ensureParamDefaults(overrides).putAll(moved);
        }
        if (bodyObj.isEmpty()) {
            overrides.remove("bodyExample");
        } else {
            overrides.put("bodyExample", bodyObj);
        }
    }

    /**
     * 相对默认测值做差分：同名且值相同的参数 / body 剔除，只保留差异。
     */
    public static JSONObject diffAgainstDefaults(JSONObject candidate, JSONObject defaults) {
        JSONObject result = new JSONObject();
        if (candidate == null || candidate.isEmpty()) {
            return result;
        }
        JSONObject defaultParams = defaults != null
                ? defaults.getJSONObject("paramDefaults")
                : null;
        if (defaultParams == null) {
            defaultParams = new JSONObject();
        }
        JSONObject candParams = candidate.getJSONObject("paramDefaults");
        if (candParams != null && !candParams.isEmpty()) {
            JSONObject kept = new JSONObject();
            for (String name : candParams.keySet()) {
                Object candVal = candParams.get(name);
                Object defVal = defaultParams.get(name);
                if (!valueEquals(candVal, defVal)) {
                    kept.put(name, candVal);
                }
            }
            if (!kept.isEmpty()) {
                result.put("paramDefaults", kept);
            }
        }
        if (candidate.containsKey("bodyExample")) {
            Object candBody = candidate.get("bodyExample");
            Object defBody = defaults != null ? defaults.get("bodyExample") : null;
            if (!valueEquals(candBody, defBody)) {
                result.put("bodyExample", candBody);
            }
        }
        return result;
    }

    /**
     * 读取 API 资产 test_value_config.request 里的默认测值（paramDefaults、bodyExample）。
     */
    public static JSONObject assetRequestValueDefaults(TestProjectApi api) {
        JSONObject out = new JSONObject();
        if (api == null || api.getTestValueConfig() == null || api.getTestValueConfig().isBlank()) {
            return out;
        }
        try {
            JSONObject root = JSON.parseObject(api.getTestValueConfig());
            if (root == null) {
                return out;
            }
            JSONObject request = root.getJSONObject("request");
            if (request == null) {
                return out;
            }
            JSONObject params = request.getJSONObject("paramDefaults");
            if (params != null && !params.isEmpty()) {
                out.put("paramDefaults", params);
            }
            if (request.containsKey("bodyExample")) {
                out.put("bodyExample", request.get("bodyExample"));
            }
        } catch (Exception ignored) {
            // 解析失败当作无默认测值
        }
        return out;
    }

    /**
     * 把已分桶的 overrides 转成节点可落盘的 Map。
     * 只序列化 paramDefaults / bodyExample；空则返回 null（调用方应删除该字段）。
     * 入参应由 {@link #buildDiffOverrides} 或 {@link #normalizeOverridesShape} 先分桶。
     */
    public static Map<String, Object> toPersistMap(JSONObject overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        JSONObject params = overrides.getJSONObject("paramDefaults");
        if (params != null && !params.isEmpty()) {
            map.put("paramDefaults", new LinkedHashMap<>(params));
        }
        if (overrides.containsKey("bodyExample")) {
            map.put("bodyExample", overrides.get("bodyExample"));
        }
        return map.isEmpty() ? null : map;
    }

    /**
     * 把临时字段 requestBody（字符串）写入 bodyExample；
     * 能解析成 JSON 则存对象，否则存原字符串。
     */
    private static void applyRequestBodyAsBodyExample(JSONObject overrides, Object requestBodyRaw) {
        if (requestBodyRaw == null) {
            return;
        }
        String bodyText = String.valueOf(requestBodyRaw).trim();
        if (bodyText.isEmpty()) {
            return;
        }
        try {
            Object parsed = JSON.parse(bodyText);
            overrides.put("bodyExample", parsed);
        } catch (Exception e) {
            overrides.put("bodyExample", bodyText);
        }
    }

    /** 比较两个测值是否相等（字符串 JSON 与对象形式视为等价）。 */
    static boolean valueEquals(Object a, Object b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        String sa = normalizeValueText(a);
        String sb = normalizeValueText(b);
        return sa.equals(sb);
    }

    /** 测值转可比较文本：JSON 字符串尽量规范化后再比。 */
    private static String normalizeValueText(Object value) {
        if (value instanceof String s) {
            String t = s.trim();
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                try {
                    return ApiConfigJsonSupport.writeCompact(ApiConfigJsonSupport.readTree(t));
                } catch (Exception ignored) {
                    return t;
                }
            }
            return t;
        }
        try {
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 把 Map / JSON 字符串 / JSONObject 转成 JSONObject。 */
    private static JSONObject toJsonObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONObject obj) {
            return obj;
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return JSON.parseObject(text);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
