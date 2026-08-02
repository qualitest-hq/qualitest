package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.project.domain.TestProjectApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 测试流 HTTP 节点上的测值覆盖字段 {@code requestValueOverrides} 的读写辅助。
 * <p>
 * 节点只存测值，不存完整请求结构。字段形状：
 * <ul>
 *   <li>{@code paramDefaults}：按参数名覆盖 query / path / header 的 value</li>
 *   <li>{@code bodyExample}：覆盖 JSON body 测值</li>
 * </ul>
 * 用途：从旧版整份 requestConfig 抽测值、相对资产默认值做差分、写成可落盘的 Map。
 * <p>
 * AI 偶发把 body 字段写在 overrides 顶层（如 {@code cartIds}/{@code addressId}），
 * 运行时只读 {@code bodyExample} 会静默忽略；{@link #normalizeOverridesShape} 负责纠正。
 */
public final class HttpNodeRequestValueOverridesSupport {

    /** 请求结构里可带 value 的参数数组字段名 */
    private static final String[] PARAM_ARRAY_FIELDS = {"queryParams", "pathParams", "declaredHeaders"};

    /** 合法的 overrides 顶层键；其余视为误放的 body 字段 */
    private static final Set<String> KNOWN_OVERRIDE_KEYS = Set.of("paramDefaults", "bodyExample");

    private HttpNodeRequestValueOverridesSupport() {
    }

    /**
     * 从整份 requestConfig 抽出非空参数 value 和 JSON body example，
     * 组成 requestValueOverrides 对象（不做差分，有值就收）。
     */
    public static JSONObject extractFromRequestConfig(Object requestConfigRaw) {
        JSONObject rc = toJsonObject(requestConfigRaw);
        JSONObject overrides = new JSONObject();
        if (rc == null || rc.isEmpty()) {
            return overrides;
        }
        JSONObject paramDefaults = new JSONObject();
        for (String field : PARAM_ARRAY_FIELDS) {
            JSONArray arr = rc.getJSONArray(field);
            if (arr == null) {
                continue;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.getString("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                Object value = item.get("value");
                if (value == null) {
                    continue;
                }
                if (value instanceof String s && s.isBlank()) {
                    continue;
                }
                paramDefaults.put(name.trim(), value);
            }
        }
        if (!paramDefaults.isEmpty()) {
            overrides.put("paramDefaults", paramDefaults);
        }
        Object bodyExample = extractBodyExample(rc);
        if (bodyExample != null) {
            overrides.put("bodyExample", bodyExample);
        }
        return overrides;
    }

    /**
     * 汇总节点上已有 overrides、整份 requestConfig 里的测值、临时 requestBody，
     * 再与 API 资产 test_value_config 中的默认测值比对，只留下不同的项。
     * 全部相同则返回空对象。
     */
    public static JSONObject buildDiffOverrides(
            Object existingOverridesRaw,
            Object requestConfigRaw,
            Object requestBodyRaw,
            TestProjectApi api) {
        JSONObject merged = new JSONObject();
        mergeOverrides(merged, toJsonObject(existingOverridesRaw));
        mergeOverrides(merged, extractFromRequestConfig(requestConfigRaw));
        applyRequestBodyAsBodyExample(merged, requestBodyRaw);
        // 合并完成后再纠正一次：覆盖「AI 把 body 字段写在顶层」的错误形状
        normalizeOverridesShapeInPlace(merged);

        JSONObject assetDefaults = assetRequestValueDefaults(api);
        return diffAgainstDefaults(merged, assetDefaults);
    }

    /**
     * 纠正错误的 overrides 形状：把顶层「非 paramDefaults/bodyExample」键提升进 bodyExample。
     * <p>
     * 接受 JSONObject / Map / JSON 字符串；返回新对象（不修改入参）。
     * 已有 bodyExample 为对象时，误放字段合并进去（同名以误放值为准）；
     * bodyExample 缺失时整段误放对象成为 bodyExample；
     * bodyExample 为非对象（如字符串）时保留原值，丢弃无法安全合并的误放字段。
     */
    public static JSONObject normalizeOverridesShape(Object raw) {
        JSONObject source = toJsonObject(raw);
        if (source == null) {
            return null;
        }
        JSONObject copy = new JSONObject(source);
        normalizeOverridesShapeInPlace(copy);
        return copy;
    }

    /** 就地纠正形状错误（仅内部与测试使用）。 */
    static void normalizeOverridesShapeInPlace(JSONObject overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        JSONObject stray = new JSONObject();
        for (String key : List.copyOf(overrides.keySet())) {
            if (!KNOWN_OVERRIDE_KEYS.contains(key)) {
                stray.put(key, overrides.remove(key));
            }
        }
        if (stray.isEmpty()) {
            return;
        }
        Object existingBody = overrides.get("bodyExample");
        if (existingBody instanceof Map<?, ?> map) {
            // JSONObject 实现 Map；统一转成 JSONObject 再合并
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
     * 把 overrides 转成节点可落盘的 Map。
     * 空对象返回 null，调用方应删除该字段。
     */
    public static Map<String, Object> toPersistMap(JSONObject overrides) {
        JSONObject normalized = normalizeOverridesShape(overrides);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        JSONObject params = normalized.getJSONObject("paramDefaults");
        if (params != null && !params.isEmpty()) {
            map.put("paramDefaults", new LinkedHashMap<>(params));
        }
        if (normalized.containsKey("bodyExample")) {
            map.put("bodyExample", normalized.get("bodyExample"));
        }
        return map.isEmpty() ? null : map;
    }

    /** 把 src 的 paramDefaults / bodyExample 合并进 target（后者覆盖同名项）。 */
    private static void mergeOverrides(JSONObject target, JSONObject src) {
        if (src == null || src.isEmpty()) {
            return;
        }
        JSONObject srcParams = src.getJSONObject("paramDefaults");
        if (srcParams != null && !srcParams.isEmpty()) {
            JSONObject targetParams = target.getJSONObject("paramDefaults");
            if (targetParams == null) {
                targetParams = new JSONObject();
                target.put("paramDefaults", targetParams);
            }
            targetParams.putAll(srcParams);
        }
        if (src.containsKey("bodyExample")) {
            target.put("bodyExample", src.get("bodyExample"));
        }
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

    /**
     * 从 requestConfig.body（json 模式）取出 example 作为 bodyExample。
     */
    private static Object extractBodyExample(JSONObject requestConfig) {
        JSONObject body = requestConfig.getJSONObject("body");
        if (body == null) {
            return null;
        }
        String mode = body.getString("mode");
        if (mode == null || !"json".equalsIgnoreCase(mode.trim())) {
            return null;
        }
        JSONObject json = body.getJSONObject("json");
        if (json == null) {
            return null;
        }
        Object example = json.get("example");
        if (example == null) {
            return null;
        }
        if (example instanceof String s) {
            if (s.isBlank()) {
                return null;
            }
            try {
                return JSON.parse(s.trim());
            } catch (Exception e) {
                return s;
            }
        }
        return example;
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
