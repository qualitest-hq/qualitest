package com.qualitest.project.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.result.TestProjectApiResult;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 读取 API 时把结构配置与测试值合成为「有效配置」。
 * <p>
 * 库中 request_config / response_config 存结构定义；test_value_config 存参数默认值、
 * body 示例、响应 example。调试转发、流程 HTTP、Web 详情、AI 读接口需要完整可用配置时，
 * 由本类把测试值叠回结构 JSON。
 */
public final class TestProjectApiEffectiveConfigResolver {

    /** 请求配置里可带调试 value 的参数数组字段 */
    private static final String[] PARAM_ARRAY_FIELDS = {"queryParams", "pathParams", "declaredHeaders"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestProjectApiEffectiveConfigResolver() {
    }

    /**
     * 由库中 API 实体合成运行时有效配置。
     * headers、cookies、前后置脚本、biz_code_config、test_value_config 原样带出。
     * api 为 null 时返回空 JSON 占位，避免空指针。
     */
    public static EffectiveApiConfig resolve(TestProjectApi api) {
        if (api == null) {
            return EffectiveApiConfig.builder()
                    .requestConfig("{}")
                    .responseConfig("{}")
                    .headers("{}")
                    .cookies("{}")
                    .build();
        }
        ObjectNode testValue = ApiConfigJsonSupport.parseObjectOrEmpty(api.getTestValueConfig());
        ObjectNode testRequest = ApiConfigJsonSupport.objectOrEmpty(testValue.get("request"));
        ObjectNode testResponse = ApiConfigJsonSupport.objectOrEmpty(testValue.get("response"));

        String requestConfig = overlayRequestValues(
                api.getRequestConfig(),
                ApiConfigJsonSupport.objectOrEmpty(testRequest.get("paramDefaults")),
                testRequest.get("bodyExample"));
        String responseConfig = overlayResponse(
                api.getResponseConfig(),
                ApiConfigJsonSupport.objectOrEmpty(testResponse.get("examplesById")));

        return EffectiveApiConfig.builder()
                .requestConfig(requestConfig)
                .responseConfig(responseConfig)
                .headers(api.getHeaders())
                .cookies(api.getCookies())
                .preRequestScript(api.getPreRequestScript())
                .postRequestScript(api.getPostRequestScript())
                .bizCodeConfig(api.getBizCodeConfig())
                .testValueConfig(api.getTestValueConfig())
                .build();
    }

    /**
     * Web 详情读路径：就地替换 Result 的 requestConfig、responseConfig 为叠加后的有效 JSON。
     * testValueConfig、bizCodeConfig 等其它列保持库中原值。
     */
    public static void overlayResultConfigs(TestProjectApiResult result) {
        if (result == null) {
            return;
        }
        EffectiveApiConfig effective = resolve(TestProjectApi.builder()
                .requestConfig(result.getRequestConfig())
                .responseConfig(result.getResponseConfig())
                .testValueConfig(result.getTestValueConfig())
                .headers(result.getHeaders())
                .cookies(result.getCookies())
                .preRequestScript(result.getPreRequestScript())
                .postRequestScript(result.getPostRequestScript())
                .bizCodeConfig(result.getBizCodeConfig())
                .build());
        result.setRequestConfig(effective.getRequestConfig());
        result.setResponseConfig(effective.getResponseConfig());
    }

    /**
     * 把 paramDefaults、bodyExample 写进 request 结构副本后序列化。
     * 既用于合成 API 有效配置，也用于把节点测值覆盖叠到有效配置上。
     *
     * @param rawRequestConfig 请求结构 JSON（或已含部分测值的副本）
     * @param paramDefaults    按参数 name 写入对应项的 value；空则跳过
     * @param bodyExample      写入 body.json.example；null 则跳过
     */
    public static String overlayRequestValues(String rawRequestConfig, ObjectNode paramDefaults, JsonNode bodyExample) {
        ObjectNode root = ApiConfigJsonSupport.parseObjectOrEmpty(rawRequestConfig);
        ObjectNode defaults = paramDefaults != null ? paramDefaults : JsonNodeFactory.instance.objectNode();
        for (String field : PARAM_ARRAY_FIELDS) {
            applyParamDefaults(root, field, defaults);
        }
        if (bodyExample != null && !bodyExample.isNull()) {
            applyBodyExampleNode(root, bodyExample);
        }
        return ApiConfigJsonSupport.writeCompact(root);
    }

    /**
     * 把节点 data.requestValueOverrides 叠到请求配置上。
     * overrides 为 null 或空时原样返回 rawRequestConfig（空则 "{}"）。
     * 支持 Map、JSON 字符串、JsonNode。
     */
    public static String overlayRequestValuesFromOverrides(String rawRequestConfig, Object overridesRaw) {
        ObjectNode overrides = toObjectNode(overridesRaw);
        if (overrides == null || overrides.isEmpty()) {
            return rawRequestConfig != null ? rawRequestConfig : "{}";
        }
        return overlayRequestValues(
                rawRequestConfig,
                ApiConfigJsonSupport.objectOrEmpty(overrides.get("paramDefaults")),
                overrides.get("bodyExample"));
    }

    /**
     * 把常见形态转成 ObjectNode，无法识别则返回 null。
     */
    private static ObjectNode toObjectNode(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ObjectNode on) {
            return on;
        }
        if (raw instanceof JsonNode jn) {
            return jn.isObject() ? (ObjectNode) jn : null;
        }
        if (raw instanceof Map<?, ?> map) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                node.set(String.valueOf(e.getKey()), MAPPER.valueToTree(e.getValue()));
            }
            return node;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return ApiConfigJsonSupport.parseObjectOrEmpty(text);
        }
        return null;
    }

    /**
     * 对指定参数数组：按 name 把 paramDefaults 中的值写入该项的 value 字段。
     */
    private static void applyParamDefaults(ObjectNode root, String field, ObjectNode paramDefaults) {
        JsonNode arrNode = root.get(field);
        if (arrNode == null || !arrNode.isArray()) {
            return;
        }
        for (JsonNode item : arrNode) {
            if (!item.isObject()) {
                continue;
            }
            ObjectNode param = (ObjectNode) item;
            String name = ApiConfigJsonSupport.textField(param, "name");
            if (name == null || !paramDefaults.has(name)) {
                continue;
            }
            JsonNode def = paramDefaults.get(name);
            if (def.isTextual()) {
                param.put("value", def.asText());
            } else {
                param.set("value", def.deepCopy());
            }
        }
    }

    /**
     * 把 bodyExample 写入 body.json.example；没有 body 对象则跳过。
     */
    private static void applyBodyExampleNode(ObjectNode root, JsonNode bodyExample) {
        if (bodyExample == null || bodyExample.isNull()) {
            return;
        }
        JsonNode body = root.get("body");
        if (body == null || !body.isObject()) {
            return;
        }
        ObjectNode bodyObj = (ObjectNode) body;
        ObjectNode jsonPart = bodyObj.has("json") && bodyObj.get("json").isObject()
                ? (ObjectNode) bodyObj.get("json")
                : JsonNodeFactory.instance.objectNode();
        jsonPart.set("example", bodyExample.isTextual()
                ? JsonNodeFactory.instance.textNode(bodyExample.asText())
                : bodyExample.deepCopy());
        bodyObj.set("json", jsonPart);
        root.set("body", bodyObj);
    }

    /**
     * 把 examplesById 里各响应 id 对应的 example 写回 responses[].example 后序列化。
     */
    private static String overlayResponse(String raw, ObjectNode examplesById) {
        ObjectNode root = ApiConfigJsonSupport.parseObjectOrEmpty(raw);
        JsonNode responsesNode = root.get("responses");
        if (responsesNode == null || !responsesNode.isArray()) {
            return ApiConfigJsonSupport.writeCompact(root);
        }
        ArrayNode responses = (ArrayNode) responsesNode;
        for (JsonNode item : responses) {
            if (!item.isObject()) {
                continue;
            }
            ObjectNode entry = (ObjectNode) item;
            String id = ApiConfigJsonSupport.textField(entry, "id");
            if (id != null && examplesById.has(id)) {
                entry.set("example", examplesById.get(id).deepCopy());
            }
        }
        return ApiConfigJsonSupport.writeCompact(root);
    }

    /**
     * 合成后的有效配置快照。
     * toApiView 可生成只替换配置字段、保留身份信息的 API 副本，供转发与脚本执行使用。
     */
    @Getter
    @Builder
    public static class EffectiveApiConfig {
        /** 叠加参数默认值与 body 示例后的 request_config */
        private final String requestConfig;
        /** 叠加用户响应 example 后的 response_config */
        private final String responseConfig;
        /** 请求头 JSON，原样传递 */
        private final String headers;
        /** Cookie JSON，原样传递 */
        private final String cookies;
        /** 前置脚本，原样传递 */
        private final String preRequestScript;
        /** 后置脚本，原样传递 */
        private final String postRequestScript;
        /** 业务 code 白名单 JSON，原样传递 */
        private final String bizCodeConfig;
        /** 测试值层原始 JSON，原样传递 */
        private final String testValueConfig;

        /**
         * 复制源 API 的 id、项目 id、路径，其余请求/响应/覆盖层字段使用本快照中的有效值。
         */
        public TestProjectApi toApiView(TestProjectApi source) {
            TestProjectApi view = new TestProjectApi();
            view.setTestProjectApiId(source.getTestProjectApiId());
            view.setTestProjectId(source.getTestProjectId());
            view.setApiPath(source.getApiPath());
            view.setRequestConfig(requestConfig);
            view.setResponseConfig(responseConfig);
            view.setHeaders(headers);
            view.setCookies(cookies);
            view.setPreRequestScript(preRequestScript);
            view.setPostRequestScript(postRequestScript);
            view.setBizCodeConfig(bizCodeConfig);
            view.setTestValueConfig(testValueConfig);
            return view;
        }
    }
}
