package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 按 JSON Schema 给请求/响应补全 example。
 * <p>
 * 请求：仅 body.mode=json 且缺 example 时生成；响应：仅 contentType=json 且缺 example 的条目生成。
 * 已有非空 example 一律不改。
 */
@Slf4j
public final class ApiConfigExampleEnricher {

    private ApiConfigExampleEnricher() {
    }

    /**
     * 给请求 body.json 补 example：有 schema、缺 example 时生成；已有样例原样返回。
     */
    public static String enrichRequestConfig(String requestConfigJson) {
        if (StrUtil.isBlank(requestConfigJson)) {
            return requestConfigJson;
        }
        try {
            JsonNode root = ApiConfigJsonSupport.readTree(requestConfigJson);
            if (!root.isObject()) {
                return requestConfigJson;
            }
            ObjectNode obj = (ObjectNode) root;
            JsonNode body = obj.get("body");
            if (body == null || !body.isObject()) {
                return requestConfigJson;
            }
            ObjectNode bodyObj = (ObjectNode) body;
            String mode = bodyObj.path("mode").asText("");
            if (!"json".equalsIgnoreCase(mode)) {
                return requestConfigJson;
            }
            JsonNode jsonBody = bodyObj.get("json");
            if (jsonBody == null || !jsonBody.isObject()) {
                return requestConfigJson;
            }
            ObjectNode jsonBodyObj = (ObjectNode) jsonBody;
            if (!exampleMissing(jsonBodyObj.get("example"))) {
                return requestConfigJson;
            }
            JsonNode schema = jsonBodyObj.get("schema");
            JsonNode generated = JsonSchemaExampleGenerator.generate(schema);
            if (generated != null) {
                jsonBodyObj.set("example", generated);
            }
            return ApiConfigJsonSupport.writeCompact(obj);
        } catch (JsonProcessingException e) {
            log.warn("enrichRequestConfig 失败，保留原配置: {}", e.getMessage());
            return requestConfigJson;
        }
    }

    /**
     * 给响应列表中缺 example 的 json 条目按 schema 生成 example；已有样例不改。
     */
    public static String enrichResponseConfig(String responseConfigJson) {
        if (StrUtil.isBlank(responseConfigJson)) {
            return responseConfigJson;
        }
        try {
            JsonNode root = ApiConfigJsonSupport.readTree(responseConfigJson);
            if (!root.isObject()) {
                return responseConfigJson;
            }
            JsonNode responses = root.get("responses");
            if (responses == null || !responses.isArray()) {
                return responseConfigJson;
            }
            boolean changed = false;
            ArrayNode arr = (ArrayNode) responses;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                if (item == null || !item.isObject()) {
                    continue;
                }
                ObjectNode entry = (ObjectNode) item;
                String ct = entry.path("contentType").asText("json");
                if (!"json".equalsIgnoreCase(ct)) {
                    continue;
                }
                if (!exampleMissing(entry.get("example"))) {
                    continue;
                }
                JsonNode schema = entry.get("schema");
                JsonNode generated = JsonSchemaExampleGenerator.generate(schema);
                if (generated != null) {
                    entry.set("example", generated);
                    changed = true;
                }
            }
            return changed ? ApiConfigJsonSupport.writeCompact(root) : responseConfigJson;
        } catch (JsonProcessingException e) {
            log.warn("enrichResponseConfig 失败，保留原配置: {}", e.getMessage());
            return responseConfigJson;
        }
    }

    /** 判断 example 是否可补全：缺失、null 或空白字符串。 */
    static boolean exampleMissing(JsonNode example) {
        if (example == null || example.isNull()) {
            return true;
        }
        if (example.isTextual()) {
            return StrUtil.isBlank(example.asText());
        }
        return false;
    }
}
