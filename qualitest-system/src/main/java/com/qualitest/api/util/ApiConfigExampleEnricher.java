package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 按 JSON Schema 为 requestConfig / responseConfig 补全 example 字段。
 * <p>
 * 导入流水线在规范化之后调用：请求侧只处理 body.mode=json；
 * 响应侧只处理 responses 中 contentType=json 的项。
 */
@Slf4j
public final class ApiConfigExampleEnricher {

    private ApiConfigExampleEnricher() {
    }

    /**
     * 当 body.mode 为 json 且存在 schema 时，生成 body.json.example。
     * 解析失败或非 json 模式时原样返回入参。
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
     * 为 responses 列表中 contentType=json 的每一项按 schema 生成 example。
     * 无变更时原样返回入参字符串。
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
}
