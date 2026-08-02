package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigBodyModes;
import com.qualitest.project.domain.TestProjectApi;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 项目 API 请求/响应配置的语义摘要。
 * <p>
 * 参数摘要含 type 与结构约束子集；schema 叶节点按 properties 展开对象字段，
 * 按数组通配 {@code [*]} 展开数组元素（不把 Schema 关键字 items 拼进路径）；不含 enum/const。
 */
public final class FlowDesignApiSummarizer {

    private static final int DEFAULT_SCHEMA_MAX_DEPTH = 6;
    private static final int DEFAULT_MAX_SCHEMA_LEAVES = 80;
    private static final int BODY_EXAMPLE_MAX = 500;
    private static final int TEST_VALUE_PREVIEW_MAX = 120;

    private static final Set<String> PARAM_CONSTRAINT_KEYS = Set.of(
            "pattern", "minLength", "maxLength", "format",
            "minValue", "maxValue", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minItems", "maxItems"
    );

    private static final Set<String> SCHEMA_CONSTRAINT_KEYS = Set.of(
            "pattern", "minLength", "maxLength", "format",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minItems", "maxItems"
    );

    private FlowDesignApiSummarizer() {
    }

    /**
     * 从 API 记录的 requestConfig 解析 HTTP 方法，缺省为 GET。
     */
    public static String resolveMethod(TestProjectApi api) {
        if (api.getRequestConfig() != null && !api.getRequestConfig().isBlank()) {
            try {
                JSONObject rc = JSON.parseObject(api.getRequestConfig());
                String method = rc.getString("method");
                if (method != null && !method.isBlank()) {
                    return method.toUpperCase(Locale.ROOT);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return "GET";
    }

    /**
     * 摘要请求侧：query/path/声明头/body 参数（含 type/约束/测值）、body 示例、body schema 叶节点。
     */
    public static JSONObject summarizeRequest(String requestConfig, String headersJson) {
        JSONObject out = new JSONObject();
        out.put("queryParams", new JSONArray());
        out.put("pathParams", new JSONArray());
        out.put("headerParams", new JSONArray());
        out.put("bodyParams", new JSONArray());
        out.put("bodyExample", "");
        out.put("bodySchemaLeaves", new JSONArray());
        if (requestConfig == null || requestConfig.isBlank()) {
            appendHeaderNames(out, headersJson);
            return out;
        }
        try {
            JSONObject rc = JSON.parseObject(requestConfig);
            out.put("queryParams", paramSummaries(rc.getJSONArray("queryParams"), true));
            out.put("pathParams", paramSummaries(rc.getJSONArray("pathParams"), true));
            JSONArray headerParams = paramSummaries(rc.getJSONArray("declaredHeaders"), true);
            if (!headerParams.isEmpty()) {
                out.put("headerParams", headerParams);
            } else {
                appendHeaderNames(out, headersJson);
            }
            JSONObject body = rc.getJSONObject("body");
            if (body != null) {
                String mode = body.getString("mode");
                if ("json".equals(mode)) {
                    JSONObject json = body.getJSONObject("json");
                    if (json != null) {
                        Object exampleNode = json.get("example");
                        String exampleText = exampleToPreview(exampleNode);
                        if (!exampleText.isEmpty()) {
                            out.put("bodyExample", truncateSemantic(exampleText, BODY_EXAMPLE_MAX));
                        }
                        Object schema = json.get("schema");
                        if (schema != null) {
                            out.put("bodySchemaLeaves", summarizeSchemaLeaves(schema));
                            if (out.getJSONArray("bodyParams").isEmpty()) {
                                out.put("bodyParams", leavesAsParamHints(out.getJSONArray("bodySchemaLeaves")));
                            }
                        } else if (exampleNode != null) {
                            out.put("bodyParams", inferJsonKeys(exampleText));
                        }
                    }
                } else if (ApiConfigBodyModes.isUrlencoded(mode)) {
                    out.put("bodyParams", paramSummaries(body.getJSONArray("urlencoded"), true));
                } else if ("formdata".equalsIgnoreCase(mode) || "form-data".equalsIgnoreCase(mode)
                        || "multipart".equalsIgnoreCase(mode)) {
                    out.put("bodyParams", paramSummaries(body.getJSONArray("formData"), true));
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return out;
    }

    /**
     * 摘要响应侧：将首个 response.schema 的叶节点扁平为「路径 → 类型名」映射（供 extracts 与 AI 读路径）。
     */
    public static JSONObject summarizeResponse(String responseConfig) {
        JSONObject summary = new JSONObject();
        if (responseConfig == null || responseConfig.isBlank()) {
            return summary;
        }
        try {
            Object parsed = JSON.parse(responseConfig);
            if (parsed instanceof JSONObject obj && obj.containsKey("responses")) {
                JSONArray responses = obj.getJSONArray("responses");
                if (responses != null && !responses.isEmpty()) {
                    Object first = responses.get(0);
                    if (first instanceof JSONObject entry && entry.get("schema") != null) {
                        JSONArray leaves = summarizeSchemaLeaves(entry.get("schema"));
                        for (int i = 0; i < leaves.size(); i++) {
                            JSONObject leaf = leaves.getJSONObject(i);
                            if (leaf == null) {
                                continue;
                            }
                            String path = leaf.getString("path");
                            String type = leaf.getString("type");
                            if (path != null && !path.isBlank()) {
                                summary.put(path, type != null ? type : "any");
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return summary;
    }

    /**
     * 首个响应 schema 的叶路径集合（去空白）；无 schema 时为空集。
     * 供断言门禁 / extract 健康检查做路径比对。
     */
    public static Set<String> summarizeResponsePaths(String responseConfig) {
        JSONObject summary = summarizeResponse(responseConfig);
        if (summary == null || summary.isEmpty()) {
            return Set.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String path : summary.keySet()) {
            if (path != null && !path.isBlank()) {
                paths.add(path.trim());
            }
        }
        return paths;
    }

    /**
     * 响应 schema 叶节点完整摘要（含约束），按响应条目分组。
     */
    public static JSONArray summarizeResponseLeaves(String responseConfig) {
        JSONArray out = new JSONArray();
        if (responseConfig == null || responseConfig.isBlank()) {
            return out;
        }
        try {
            Object parsed = JSON.parse(responseConfig);
            if (!(parsed instanceof JSONObject obj) || !obj.containsKey("responses")) {
                return out;
            }
            JSONArray responses = obj.getJSONArray("responses");
            if (responses == null) {
                return out;
            }
            for (int i = 0; i < responses.size(); i++) {
                Object item = responses.get(i);
                if (!(item instanceof JSONObject entry)) {
                    continue;
                }
                Object schema = entry.get("schema");
                if (schema == null) {
                    continue;
                }
                String responseId = entry.getString("id");
                JSONArray leaves = summarizeSchemaLeaves(schema);
                for (int j = 0; j < leaves.size(); j++) {
                    JSONObject leaf = leaves.getJSONObject(j);
                    if (leaf == null) {
                        continue;
                    }
                    JSONObject row = new JSONObject(leaf);
                    if (responseId != null && !responseId.isBlank()) {
                        row.put("responseId", responseId);
                    }
                    out.add(row);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return out;
    }

    /**
     * 值层摘要：paramDefaults 键与预览、bodyExample 截断、examplesById 键列表（不含完整大对象时可截断）。
     */
    public static JSONObject summarizeTestValueConfig(String testValueConfig) {
        JSONObject out = new JSONObject();
        out.put("paramDefaults", new JSONArray());
        out.put("bodyExample", "");
        out.put("responseExampleIds", new JSONArray());
        if (testValueConfig == null || testValueConfig.isBlank()) {
            return out;
        }
        try {
            JSONObject root = JSON.parseObject(testValueConfig);
            JSONObject request = root.getJSONObject("request");
            if (request != null) {
                JSONObject defaults = request.getJSONObject("paramDefaults");
                if (defaults != null) {
                    JSONArray rows = new JSONArray();
                    for (String key : defaults.keySet()) {
                        if (key == null || key.isBlank()) {
                            continue;
                        }
                        JSONObject row = new JSONObject();
                        row.put("name", key.trim());
                        row.put("valuePreview", truncateSemantic(String.valueOf(defaults.get(key)), TEST_VALUE_PREVIEW_MAX));
                        rows.add(row);
                    }
                    out.put("paramDefaults", rows);
                }
                Object bodyExample = request.get("bodyExample");
                if (bodyExample != null) {
                    out.put("bodyExample", truncateSemantic(exampleToPreview(bodyExample), BODY_EXAMPLE_MAX));
                }
            }
            JSONObject response = root.getJSONObject("response");
            if (response != null) {
                JSONObject byId = response.getJSONObject("examplesById");
                if (byId != null) {
                    JSONArray ids = new JSONArray();
                    for (String id : byId.keySet()) {
                        if (id != null && !id.isBlank()) {
                            ids.add(id.trim());
                        }
                    }
                    out.put("responseExampleIds", ids);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return out;
    }

    /**
     * 根据响应 schema 摘要生成建议的 extracts 列表。
     */
    public static JSONArray suggestExtracts(JSONObject responseSchemaSummary, String dataPath) {
        JSONArray extracts = new JSONArray();
        if (responseSchemaSummary == null || responseSchemaSummary.isEmpty()) {
            return extracts;
        }
        String prefix = (dataPath == null || dataPath.isBlank()) ? "data" : dataPath.trim();
        String pathPrefix = prefix + ".";
        for (String path : responseSchemaSummary.keySet()) {
            if (path == null || !path.startsWith(pathPrefix)) {
                continue;
            }
            String field = path.substring(pathPrefix.length());
            if (field.isBlank()) {
                continue;
            }
            String name = field.contains(".")
                    ? field.substring(field.lastIndexOf('.') + 1)
                    : field;
            if (name.isBlank()) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("name", name);
            row.put("expr", "$." + path);
            row.put("scope", "flow");
            row.put("from", "body");
            extracts.add(row);
        }
        return extracts;
    }

    /**
     * 将 JSON Schema 展开为叶节点数组：每项含 path / type / 约束子集（不含 enum）。
     * 对象走 properties 键名；数组在路径上追加 [*] 再展开元素 schema。
     */
    public static JSONArray summarizeSchemaLeaves(Object schemaRoot) {
        JSONArray leaves = new JSONArray();
        walkSchemaLeaves(schemaRoot, "", 0, DEFAULT_SCHEMA_MAX_DEPTH, leaves, DEFAULT_MAX_SCHEMA_LEAVES);
        return leaves;
    }

    /**
     * 递归展开 schema。
     * 对象：path 追加属性名；数组：path 追加 [*]（真实 JSON 用下标/通配访问元素，没有 items 键）。
     */
    private static void walkSchemaLeaves(Object node, String prefix, int depth, int maxDepth,
                                         JSONArray leaves, int maxLeaves) {
        if (node == null || depth > maxDepth || leaves.size() >= maxLeaves) {
            return;
        }
        if (!(node instanceof JSONObject obj)) {
            return;
        }
        String type = resolveSchemaType(obj);
        JSONObject properties = obj.getJSONObject("properties");
        Object items = obj.get("items");

        if ("object".equals(type) && properties != null && !properties.isEmpty()) {
            for (String key : properties.keySet()) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                String path = prefix.isEmpty() ? key.trim() : prefix + "." + key.trim();
                walkSchemaLeaves(properties.get(key), path, depth + 1, maxDepth, leaves, maxLeaves);
            }
            return;
        }
        if ("array".equals(type) && items != null) {
            // 数组路径用 [*]：例如 data[*].quantity，而不是 data.items.quantity
            String path = prefix.isEmpty() ? "[*]" : prefix + "[*]";
            walkSchemaLeaves(items, path, depth + 1, maxDepth, leaves, maxLeaves);
            return;
        }
        if (prefix.isEmpty()) {
            return;
        }
        JSONObject leaf = new JSONObject();
        leaf.put("path", prefix);
        leaf.put("type", type != null && !type.isBlank() ? type : "any");
        copyConstraintKeys(obj, leaf, SCHEMA_CONSTRAINT_KEYS);
        if (Boolean.TRUE.equals(obj.getBoolean("required"))) {
            leaf.put("required", true);
        }
        String description = obj.getString("description");
        if (description != null && !description.isBlank()) {
            leaf.put("description", truncateSemantic(description, 80));
        }
        leaves.add(leaf);
    }

    private static String resolveSchemaType(JSONObject obj) {
        String type = obj.getString("type");
        if (type != null && !type.isBlank()) {
            return type.trim().toLowerCase(Locale.ROOT);
        }
        if (obj.getJSONObject("properties") != null) {
            return "object";
        }
        if (obj.get("items") != null) {
            return "array";
        }
        return "any";
    }

    private static void appendHeaderNames(JSONObject out, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            Object parsed = JSON.parse(headersJson);
            if (parsed instanceof JSONObject obj) {
                JSONArray summaries = new JSONArray();
                for (String key : obj.keySet()) {
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    JSONObject summary = new JSONObject();
                    summary.put("name", key.trim());
                    summary.put("required", false);
                    summaries.add(summary);
                }
                if (!summaries.isEmpty()) {
                    out.put("headerParams", summaries);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * @param includeValue 是否输出 value 预览（有效配置叠加测值后可读）
     */
    private static JSONArray paramSummaries(JSONArray arr, boolean includeValue) {
        JSONArray summaries = new JSONArray();
        if (arr == null) {
            return summaries;
        }
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (!(item instanceof JSONObject obj)) {
                continue;
            }
            String name = obj.getString("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            JSONObject summary = new JSONObject();
            summary.put("name", name.trim());
            summary.put("required", Boolean.TRUE.equals(obj.getBoolean("required")));
            String type = obj.getString("type");
            if (type != null && !type.isBlank()) {
                summary.put("type", type.trim().toLowerCase(Locale.ROOT));
            }
            copyConstraintKeys(obj, summary, PARAM_CONSTRAINT_KEYS);
            String description = obj.getString("description");
            if (description != null && !description.isBlank()) {
                summary.put("description", truncateSemantic(description, 80));
            }
            if (includeValue) {
                Object value = obj.get("value");
                if (value != null && !String.valueOf(value).isBlank()) {
                    summary.put("valuePreview", truncateSemantic(String.valueOf(value), TEST_VALUE_PREVIEW_MAX));
                }
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private static void copyConstraintKeys(JSONObject source, JSONObject target, Set<String> keys) {
        for (String key : keys) {
            if (!source.containsKey(key)) {
                continue;
            }
            Object val = source.get(key);
            if (val == null) {
                continue;
            }
            if (val instanceof String s && s.isBlank()) {
                continue;
            }
            target.put(key, val);
        }
    }

    private static JSONArray leavesAsParamHints(JSONArray leaves) {
        JSONArray names = new JSONArray();
        if (leaves == null) {
            return names;
        }
        for (int i = 0; i < leaves.size(); i++) {
            JSONObject leaf = leaves.getJSONObject(i);
            if (leaf == null) {
                continue;
            }
            String path = leaf.getString("path");
            if (path == null || path.isBlank() || path.contains(".")) {
                continue;
            }
            JSONObject summary = new JSONObject();
            summary.put("name", path);
            summary.put("required", Boolean.TRUE.equals(leaf.getBoolean("required")));
            if (leaf.getString("type") != null) {
                summary.put("type", leaf.getString("type"));
            }
            names.add(summary);
        }
        return names;
    }

    private static JSONArray inferJsonKeys(String example) {
        JSONArray names = new JSONArray();
        if (example == null || example.isBlank()) {
            return names;
        }
        try {
            Object parsed = JSON.parse(example.trim());
            if (parsed instanceof JSONObject obj) {
                for (String key : obj.keySet()) {
                    JSONObject summary = new JSONObject();
                    summary.put("name", key);
                    summary.put("required", false);
                    names.add(summary);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return names;
    }

    private static String exampleToPreview(Object example) {
        if (example == null) {
            return "";
        }
        if (example instanceof String s) {
            return s;
        }
        try {
            return JSON.toJSONString(example);
        } catch (Exception ignored) {
            return String.valueOf(example);
        }
    }

    private static String truncateSemantic(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}
