package com.qualitest.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualitest.common.exception.ServiceException;

/**
 * API 配置 JSON 的公共读写与校验工具。
 * <p>
 * 统一 ObjectMapper、configVersion 约定，以及解析失败时返回空对象/空数组的安全方法，
 * 供导入规范化、结构合并、有效配置合成等使用。
 */
public final class ApiConfigJsonSupport {

    /** 请求/响应配置及导入包的协议版本号，当前固定为 1 */
    public static final int CONFIG_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiConfigJsonSupport() {
    }

    /**
     * 将 JSON 字符串解析为树节点。
     *
     * @throws JsonProcessingException 不是合法 JSON 时抛出
     */
    public static JsonNode readTree(String raw) throws JsonProcessingException {
        return MAPPER.readTree(raw);
    }

    /**
     * 将树节点序列化为紧凑 JSON 字符串（无多余空白）。
     *
     * @throws IllegalStateException 序列化失败时抛出
     */
    public static String writeCompact(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize API config JSON", e);
        }
    }

    /**
     * 校验根对象上的 configVersion 必须等于当前协议版本（1）。
     *
     * @param obj   配置根对象
     * @param label 字段路径前缀，用于错误信息（如 requestConfig）
     */
    public static void requireConfigVersion(ObjectNode obj, String label) {
        JsonNode version = obj.get("configVersion");
        if (version == null || !version.isNumber() || version.asInt() != CONFIG_VERSION) {
            throw new ServiceException(label + ".configVersion 必须为 1");
        }
    }

    /**
     * 深拷贝 JSON 数组；节点缺失或非数组时返回新的空数组。
     */
    public static ArrayNode copyArrayOrEmpty(JsonNode node) {
        if (node != null && node.isArray()) {
            return (ArrayNode) node.deepCopy();
        }
        return JsonNodeFactory.instance.arrayNode();
    }

    /**
     * 解析 JSON 字符串为对象节点。
     * null、空白、非法 JSON、非对象根节点时返回空对象，不抛异常。
     */
    public static ObjectNode parseObjectOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode node = readTree(json);
            return node.isObject() ? (ObjectNode) node.deepCopy() : JsonNodeFactory.instance.objectNode();
        } catch (JsonProcessingException e) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    /**
     * 节点为对象则返回该对象，否则返回新的空对象（不修改原树）。
     */
    public static ObjectNode objectOrEmpty(JsonNode node) {
        return node != null && node.isObject() ? (ObjectNode) node : JsonNodeFactory.instance.objectNode();
    }

    /**
     * 节点为数组则返回该数组引用，否则返回新的空数组。
     */
    public static ArrayNode arrayOrEmpty(JsonNode node) {
        if (node != null && node.isArray()) {
            return (ArrayNode) node;
        }
        return JsonNodeFactory.instance.arrayNode();
    }

    /**
     * 读取 parent 下名为 field 的子对象；不存在或类型不对时在 parent 上创建空对象并返回。
     * 用于惰性初始化 test_value_config 下的 request、response、paramDefaults 等嵌套节。
     */
    public static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode created = JsonNodeFactory.instance.objectNode();
        parent.set(field, created);
        return created;
    }

    /**
     * 读取对象上的字符串字段：存在且为文本、trim 后非空才返回，否则 null。
     * 用于读取参数 name、响应 id 等对齐键。
     */
    public static String textField(JsonNode obj, String field) {
        if (obj == null || !obj.isObject()) {
            return null;
        }
        JsonNode node = obj.get(field);
        if (node == null || !node.isTextual()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
