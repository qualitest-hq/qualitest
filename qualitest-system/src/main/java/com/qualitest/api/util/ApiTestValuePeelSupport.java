package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;

/**
 * 把请求/响应结构里的调试测值拆出去，写入测值配置。
 * <p>
 * 结构侧只保留参数定义与 schema；参数 value、body.example、responses[].example
 * 分别落到 paramDefaults、bodyExample、examplesById。
 */
public final class ApiTestValuePeelSupport {

    /** 请求结构里带 value 的参数数组字段 */
    private static final String[] PARAM_ARRAY_FIELDS = {"queryParams", "pathParams", "declaredHeaders"};

    private ApiTestValuePeelSupport() {
    }

    /**
     * 一次剥离后的三份 JSON：干净的 request、干净的 response、合并后的测值。
     */
    @Getter
    @Builder
    public static class PeelResult {
        /** 去掉测值后的 request_config */
        private final String requestConfig;
        /** 去掉测值后的 response_config */
        private final String responseConfig;
        /** 合并后的 test_value_config */
        private final String testValueConfig;
    }

    /**
     * 从 request / response 抽出测值，合并进已有测值配置后返回三份结果。
     *
     * @param requestConfigJson     可能仍带 value / body.example 的请求 JSON
     * @param responseConfigJson    可能仍带 responses[].example 的响应 JSON
     * @param existingTestValueJson 已有测值 JSON，可为 null
     */
    public static PeelResult peel(String requestConfigJson, String responseConfigJson, String existingTestValueJson) {
        ObjectNode testRoot = ApiConfigJsonSupport.parseObjectOrEmpty(existingTestValueJson);
        ObjectNode testRequest = ApiConfigJsonSupport.ensureObject(testRoot, "request");
        ObjectNode testResponse = ApiConfigJsonSupport.ensureObject(testRoot, "response");

        ObjectNode request = ApiConfigJsonSupport.parseObjectOrEmpty(requestConfigJson);
        peelRequest(request, testRequest);

        ObjectNode response = ApiConfigJsonSupport.parseObjectOrEmpty(responseConfigJson);
        peelResponse(response, testResponse);

        return PeelResult.builder()
                .requestConfig(ApiConfigJsonSupport.writeCompact(request))
                .responseConfig(ApiConfigJsonSupport.writeCompact(response))
                .testValueConfig(ApiConfigJsonSupport.writeCompact(testRoot))
                .build();
    }

    /**
     * 就地剥离请求测值：参数 value → paramDefaults，body.json.example → bodyExample，并从结构里删掉。
     */
    public static void peelRequest(ObjectNode request, ObjectNode testRequest) {
        if (request == null || testRequest == null) {
            return;
        }
        ObjectNode paramDefaults = ApiConfigJsonSupport.ensureObject(testRequest, "paramDefaults");
        for (String field : PARAM_ARRAY_FIELDS) {
            peelParamArray(request.get(field), paramDefaults);
        }
        JsonNode body = request.get("body");
        if (body != null && body.isObject()) {
            ObjectNode bodyObj = (ObjectNode) body;
            peelParamArray(bodyObj.get("formData"), paramDefaults);
            peelParamArray(bodyObj.get("urlencoded"), paramDefaults);
            JsonNode jsonPart = bodyObj.get("json");
            if (jsonPart != null && jsonPart.isObject()) {
                ObjectNode jsonObj = (ObjectNode) jsonPart;
                JsonNode example = jsonObj.get("example");
                if (example != null && !example.isNull()) {
                    testRequest.set("bodyExample", example.deepCopy());
                    jsonObj.remove("example");
                }
            }
        }
        if (paramDefaults.isEmpty()) {
            testRequest.remove("paramDefaults");
        }
    }

    /**
     * 就地剥离响应测值：有 id 的条目把 example 写入 examplesById，并从结构里删掉。
     */
    public static void peelResponse(ObjectNode response, ObjectNode testResponse) {
        if (response == null || testResponse == null) {
            return;
        }
        ArrayNode responses = ApiConfigJsonSupport.arrayOrEmpty(response.get("responses"));
        ObjectNode examplesById = ApiConfigJsonSupport.ensureObject(testResponse, "examplesById");
        for (JsonNode item : responses) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode entry = (ObjectNode) item;
            String id = ApiConfigJsonSupport.textField(entry, "id");
            JsonNode example = entry.get("example");
            if (id != null && example != null && !example.isNull()) {
                examplesById.set(id, example.deepCopy());
                entry.remove("example");
            }
        }
        if (examplesById.isEmpty()) {
            testResponse.remove("examplesById");
        }
    }

    /** 遍历参数数组：非空 value 写入 paramDefaults，并从行上删除 value。 */
    private static void peelParamArray(JsonNode arrNode, ObjectNode paramDefaults) {
        if (arrNode == null || !arrNode.isArray() || paramDefaults == null) {
            return;
        }
        for (JsonNode item : arrNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode row = (ObjectNode) item;
            String name = ApiConfigJsonSupport.textField(row, "name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            JsonNode valueNode = row.get("value");
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }
            String value = valueNode.isTextual() ? valueNode.asText() : ApiConfigJsonSupport.writeCompact(valueNode);
            if (StrUtil.isNotBlank(value)) {
                paramDefaults.put(name, value);
            }
            row.remove("value");
        }
    }
}
