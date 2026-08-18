package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigBodyModes;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.http.SuccessCheckResolver;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 检查成功路径上的项目 HTTP 节点是否缺少必填测值。
 * <p>
 * JSON body 只看节点 requestValueOverrides.bodyExample，没有则当成空对象。
 * query、path、表单字段看叠层后的 value。
 * successCheck.mode=off 时不检查（故意缺字段的失败场景）。
 * 不会把接口资产 example 里的默认值写进节点。
 */
public final class HttpRequiredParamGate {

    private HttpRequiredParamGate() {
    }

    /**
     * 遍历图中已绑定项目接口的 HTTP 节点，缺必填测值则记入错误列表。
     *
     * @param graph       待检查的流程图
     * @param apiResolver 按接口 id 加载接口；返回 null 则跳过该节点
     * @return 错误文案；空列表表示无需检查或已满足
     */
    public static List<String> validate(GraphJson graph, Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        if (apiResolver == null) {
            return errors;
        }
        FlowHttpNodeVisitor.visitProjectBound(graph, (node, data, apiId) -> {
            // 预期业务拒绝：节点关掉了业务码校验，允许故意不填必填
            if (SuccessCheckResolver.isOff(data)) {
                return;
            }
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null) {
                return;
            }
            String nodeLabel = FlowHttpNodeVisitor.resolveNodeName(data, node.getId());
            if (StrUtil.isBlank(nodeLabel)) {
                nodeLabel = "HTTP 节点";
            }
            JSONObject issued = TestProjectApiEffectiveConfigResolver.issuedRequestConfig(api, data);
            checkParamArray(issued.getJSONArray("queryParams"), "query", nodeLabel, errors);
            checkParamArray(issued.getJSONArray("pathParams"), "path", nodeLabel, errors);
            checkJsonBody(issued, nodeLabel, errors);
            checkFormLikeParams(issued, nodeLabel, errors);
        });
        return errors;
    }

    /** 检查 query / path / 表单参数数组：required=true 且已启用的行，value 为空则记错。 */
    private static void checkParamArray(JSONArray params, String kind, String nodeLabel, List<String> errors) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            JSONObject row = params.getJSONObject(i);
            if (row == null || Boolean.FALSE.equals(row.getBoolean("_enabled"))) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getBoolean("required"))) {
                continue;
            }
            String name = row.getString("name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            if (isMissing(row.get("value"))) {
                errors.add(missingMessage(nodeLabel, kind, name.trim()));
            }
        }
    }

    /** 检查 urlencoded / form-data 请求体里标了 required 的字段。 */
    private static void checkFormLikeParams(JSONObject requestConfig, String nodeLabel, List<String> errors) {
        JSONObject body = requestConfig.getJSONObject("body");
        if (body == null) {
            return;
        }
        String mode = body.getString("mode");
        if (ApiConfigBodyModes.isUrlencoded(mode)) {
            checkParamArray(body.getJSONArray("urlencoded"), "body", nodeLabel, errors);
            return;
        }
        if (ApiConfigBodyModes.isFormData(mode)) {
            checkParamArray(body.getJSONArray("formData"), "body", nodeLabel, errors);
        }
    }

    /** 按 JSON schema 的 required 数组检查 body.example 是否缺字段。 */
    private static void checkJsonBody(JSONObject requestConfig, String nodeLabel, List<String> errors) {
        JSONObject body = requestConfig.getJSONObject("body");
        if (body == null || !"json".equalsIgnoreCase(StrUtil.trim(body.getString("mode")))) {
            return;
        }
        JSONObject json = body.getJSONObject("json");
        if (json == null) {
            return;
        }
        JSONObject schema = json.getJSONObject("schema");
        if (schema == null) {
            return;
        }
        JSONObject actual = readJsonExample(json.get("example"));
        walkJsonRequired(schema, actual, "", nodeLabel, errors);
    }

    /** 把 body.example 收成对象；字符串会先 parse，解析失败或非对象则当成空对象。 */
    private static JSONObject readJsonExample(Object example) {
        if (example instanceof JSONObject obj) {
            return obj;
        }
        if (example instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (example instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parse(s.trim());
                if (parsed instanceof JSONObject obj) {
                    return obj;
                }
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
        return new JSONObject();
    }

    /**
     * 按当前对象 schema.required 检查 actual 是否缺字段，再进入子对象 / 数组元素继续查。
     */
    private static void walkJsonRequired(
            JSONObject schema,
            JSONObject actual,
            String pathPrefix,
            String nodeLabel,
            List<String> errors) {
        if (schema == null) {
            return;
        }
        JSONArray required = schema.getJSONArray("required");
        if (required != null) {
            for (int i = 0; i < required.size(); i++) {
                String name = required.getString(i);
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                String fieldPath = joinPath(pathPrefix, name.trim());
                Object value = actual != null ? actual.get(name.trim()) : null;
                if (isMissing(value)) {
                    errors.add(missingMessage(nodeLabel, "body", fieldPath));
                }
            }
        }
        JSONObject properties = schema.getJSONObject("properties");
        if (properties == null || actual == null) {
            return;
        }
        for (String key : properties.keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JSONObject childSchema = properties.getJSONObject(key);
            if (childSchema == null) {
                continue;
            }
            Object childVal = actual.get(key);
            String childPath = joinPath(pathPrefix, key.trim());
            if (childVal instanceof JSONObject childObj) {
                walkJsonRequired(childSchema, childObj, childPath, nodeLabel, errors);
            } else if (childVal instanceof JSONArray arr) {
                JSONObject itemsSchema = childSchema.getJSONObject("items");
                if (itemsSchema == null) {
                    continue;
                }
                for (int i = 0; i < arr.size(); i++) {
                    Object el = arr.get(i);
                    if (el instanceof JSONObject elObj) {
                        walkJsonRequired(itemsSchema, elObj, childPath + "[" + i + "]", nodeLabel, errors);
                    }
                }
            }
        }
    }

    /** 拼嵌套字段路径，如 items[0].skuId。 */
    private static String joinPath(String prefix, String name) {
        return prefix == null || prefix.isEmpty() ? name : prefix + "." + name;
    }

    /**
     * 缺、null、空串、空数组视为未填；数字 0 与布尔 false 视为已填。
     */
    static boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof JSONArray arr) {
            return arr.isEmpty();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    /** 缺必填时的中文错误：提示补节点测值，不要去改接口资产上的 required 勾选。 */
    private static String missingMessage(String nodeLabel, String kind, String field) {
        return "HTTP 节点「" + nodeLabel + "」缺少必填 " + kind + " 字段 " + field
                + "。请在节点测值中补上，不要改接口资产的 required。";
    }
}
