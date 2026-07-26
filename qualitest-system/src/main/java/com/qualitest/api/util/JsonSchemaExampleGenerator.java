package com.qualitest.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * 根据 JSON Schema 生成示例 JSON
 */
public final class JsonSchemaExampleGenerator {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private static final int MAX_DEPTH = 40;

    private static final int MAX_OBJECT_PROPERTIES = 256;

    /** 数组示例元素个数：固定为 1 */
    private static final int ARRAY_EXAMPLE_SIZE = 1;

    private JsonSchemaExampleGenerator() {
    }

    /**
     * @param schema JSON Schema 节点；null 或空对象时返回 null
     */
    public static JsonNode generate(JsonNode schema) {
        if (schema == null || schema.isNull()) {
            return null;
        }
        if (isEffectivelyEmptySchema(schema)) {
            return null;
        }
        return generateValue(schema, 0);
    }

    private static boolean isEffectivelyEmptySchema(JsonNode schema) {
        if (!schema.isObject()) {
            return false;
        }
        if (schema.has("type") || schema.has("properties") || schema.has("items")
                || schema.has("$ref") || schema.has("oneOf") || schema.has("anyOf")
                || schema.has("allOf") || schema.has("enum")) {
            return false;
        }
        return schema.size() == 0;
    }

    private static JsonNode generateValue(JsonNode schema, int depth) {
        if (schema == null || schema.isNull() || depth > MAX_DEPTH) {
            return NullNode.getInstance();
        }

        if (isArraySchema(schema)) {
            if (schema.has("example") && !schema.get("example").isNull()) {
                return toSingleElementArray(schema.get("example"), schema, depth);
            }
            if (schema.has("const") && !schema.get("const").isNull()) {
                return toSingleElementArray(schema.get("const"), schema, depth);
            }
            if (schema.has("default") && !schema.get("default").isNull()) {
                return toSingleElementArray(schema.get("default"), schema, depth);
            }
            if (schema.has("enum") && schema.get("enum").isArray() && !schema.get("enum").isEmpty()) {
                return toSingleElementArray(schema.get("enum").get(0), schema, depth);
            }
            return generateArray(schema, depth);
        }

        if (schema.has("example") && !schema.get("example").isNull()) {
            return schema.get("example").deepCopy();
        }
        if (schema.has("const")) {
            return schema.get("const").deepCopy();
        }
        if (schema.has("default")) {
            return schema.get("default").deepCopy();
        }
        if (schema.has("enum") && schema.get("enum").isArray() && !schema.get("enum").isEmpty()) {
            return schema.get("enum").get(0).deepCopy();
        }

        String type = resolvePrimaryType(schema);
        if ("object".equals(type) || (type == null && schema.has("properties"))) {
            return generateObject(schema, depth);
        }
        if ("integer".equals(type)) {
            return NF.numberNode(0);
        }
        if ("number".equals(type)) {
            return NF.numberNode(0);
        }
        if ("boolean".equals(type)) {
            return NF.booleanNode(false);
        }
        if ("null".equals(type)) {
            return NullNode.getInstance();
        }
        return NF.textNode("");
    }

    private static ObjectNode generateObject(JsonNode schema, int depth) {
        ObjectNode out = NF.objectNode();
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            return out;
        }
        int count = 0;
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext() && count < MAX_OBJECT_PROPERTIES) {
            Map.Entry<String, JsonNode> e = it.next();
            out.set(e.getKey(), generateValue(e.getValue(), depth + 1));
            count++;
        }
        return out;
    }

    /**
     * 数组示例：有且仅有 {@link #ARRAY_EXAMPLE_SIZE}（1）个元素。
     */
    private static ArrayNode generateArray(JsonNode schema, int depth) {
        ArrayNode arr = NF.arrayNode(ARRAY_EXAMPLE_SIZE);
        JsonNode items = schema.get("items");
        if (items != null && !items.isNull()) {
            arr.add(generateValue(items, depth + 1));
        }
        return arr;
    }

    private static boolean isArraySchema(JsonNode schema) {
        String type = resolvePrimaryType(schema);
        return "array".equals(type) || (type == null && schema.has("items"));
    }

    /**
     * 将任意节点规范为仅含 1 个元素的数组（数组类型 schema 专用）。
     */
    private static ArrayNode toSingleElementArray(JsonNode candidate, JsonNode schema, int depth) {
        ArrayNode arr = NF.arrayNode(ARRAY_EXAMPLE_SIZE);
        if (candidate != null && candidate.isArray()) {
            if (!candidate.isEmpty()) {
                arr.add(candidate.get(0).deepCopy());
                return arr;
            }
            return generateArray(schema, depth);
        }
        if (candidate != null && !candidate.isNull()) {
            arr.add(candidate.deepCopy());
            return arr;
        }
        return generateArray(schema, depth);
    }

    private static String resolvePrimaryType(JsonNode schema) {
        JsonNode typeNode = schema.get("type");
        if (typeNode == null || typeNode.isNull()) {
            return null;
        }
        if (typeNode.isTextual()) {
            return typeNode.asText();
        }
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (t.isTextual() && !"null".equals(t.asText())) {
                    return t.asText();
                }
            }
        }
        return null;
    }
}
