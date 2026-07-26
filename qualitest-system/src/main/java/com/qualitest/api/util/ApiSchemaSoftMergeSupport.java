package com.qualitest.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 导入时结构层字段级 soft merge。
 * <p>
 * 形状以上传包为准；字段主类型未变时，把本地用户约束写回结果。
 * 作用于参数节点与 JSON Schema 树（body / response）；值层 example 不在此处理。
 * 类型比较时 {@code text} 按 {@code string} 计算。
 */
public final class ApiSchemaSoftMergeSupport {

    /**
     * 用户可改约束键：类型未变时从本地拷到合并结果，覆盖上传包同名字段。
     */
    static final Set<String> USER_CONSTRAINT_KEYS = Set.of(
            "pattern",
            "minLength",
            "maxLength",
            "format",
            "minimum",
            "maximum",
            "exclusiveMinimum",
            "exclusiveMaximum",
            "minValue",
            "maxValue",
            "minItems",
            "maxItems"
    );

    private ApiSchemaSoftMergeSupport() {
    }

    /**
     * 递归合并 JSON Schema 节点。
     * <ul>
     *   <li>上传包为 null → 返回 null（属性删除）</li>
     *   <li>本地无或类型变更 → 采用上传包拷贝</li>
     *   <li>类型未变 → 上传包形状 + 本地约束；properties/items 递归</li>
     * </ul>
     */
    public static JsonNode mergeSchema(JsonNode local, JsonNode incoming) {
        if (incoming == null || incoming.isNull()) {
            return null;
        }
        if (!incoming.isObject()) {
            return incoming.deepCopy();
        }
        if (local == null || local.isNull() || !local.isObject()) {
            return incoming.deepCopy();
        }
        if (isTypeChanged(local, incoming)) {
            return incoming.deepCopy();
        }

        ObjectNode out = incoming.deepCopy();
        applyLocalConstraints(local, out);
        mergeObjectProperties(local, incoming, out);
        mergeArrayItems(local, incoming, out);
        return out;
    }

    /**
     * 合并参数对象：以上传包为基线（调用方应已去掉 value 并交出可写副本），类型未变则写回本地约束。
     * 会原地改写 {@code incomingWithoutValue}，不再二次 deepCopy。
     */
    public static ObjectNode mergeParamNode(JsonNode local, ObjectNode incomingWithoutValue) {
        ObjectNode out = incomingWithoutValue != null
                ? incomingWithoutValue
                : JsonNodeFactory.instance.objectNode();
        if (local != null && local.isObject() && !isTypeChanged(local, out)) {
            applyLocalConstraints(local, out);
        }
        return out;
    }

    /**
     * 将 merge 结果写入 parent 字段：null 则 putNull，否则 set。
     */
    public static void setOrNull(ObjectNode parent, String field, JsonNode value) {
        if (parent == null || field == null) {
            return;
        }
        if (value == null) {
            parent.putNull(field);
        } else {
            parent.set(field, value);
        }
    }

    /**
     * 判断结构类型是否变更：比较归一后的主 type；
     * 主类型为 array 时再比较 items 的主 type。
     * 两边 type 都未声明（空串）视为未变。
     *
     * @param local    库中已有节点
     * @param incoming 上传包节点
     * @return true 表示类型已变，合并时不应再保留本地约束
     */
    public static boolean isTypeChanged(JsonNode local, JsonNode incoming) {
        String localType = normalizeType(local);
        String incomingType = normalizeType(incoming);
        if (!localType.equals(incomingType)) {
            return true;
        }
        if ("array".equals(incomingType)) {
            String localItems = normalizeItemsType(local);
            String incomingItems = normalizeItemsType(incoming);
            return !localItems.equals(incomingItems);
        }
        return false;
    }

    /**
     * 把本地用户约束键拷贝到 out（覆盖同名字段）。
     * 仅拷贝 {@link #USER_CONSTRAINT_KEYS} 中、本地非 null 的键。
     */
    static void applyLocalConstraints(JsonNode local, ObjectNode out) {
        for (String key : USER_CONSTRAINT_KEYS) {
            JsonNode value = local.get(key);
            if (value != null && !value.isNull()) {
                out.set(key, value.deepCopy());
            }
        }
    }

    /** 按上传包 properties 的键递归合并子 schema，结果写回 out.properties。 */
    private static void mergeObjectProperties(JsonNode local, JsonNode incoming, ObjectNode out) {
        JsonNode incomingProps = incoming.get("properties");
        if (incomingProps == null || !incomingProps.isObject()) {
            return;
        }
        JsonNode localProps = local.get("properties");
        ObjectNode mergedProps = JsonNodeFactory.instance.objectNode();
        Iterator<String> names = incomingProps.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode localChild = localProps != null && localProps.isObject() ? localProps.get(name) : null;
            JsonNode incomingChild = incomingProps.get(name);
            JsonNode mergedChild = mergeSchema(localChild, incomingChild);
            if (mergedChild != null) {
                mergedProps.set(name, mergedChild);
            }
        }
        out.set("properties", mergedProps);
    }

    /** 合并 array 的 items 子节点，写回 out.items。 */
    private static void mergeArrayItems(JsonNode local, JsonNode incoming, ObjectNode out) {
        JsonNode incomingItems = incoming.get("items");
        if (incomingItems == null || incomingItems.isNull()) {
            return;
        }
        JsonNode localItems = local.get("items");
        setOrNull(out, "items", mergeSchema(localItems, incomingItems));
    }

    /**
     * 读取对象节点的 {@code type} 用于比较：去空白、转小写；
     * {@code text} 归一为 {@code string}；缺失或非文本则返回空串。
     */
    private static String normalizeType(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode type = node.get("type");
        if (type == null || type.isNull() || !type.isTextual()) {
            return "";
        }
        String normalized = type.asText().trim().toLowerCase(Locale.ROOT);
        if ("text".equals(normalized)) {
            return "string";
        }
        return normalized;
    }

    /** 读取数组 schema 的 items 主类型（走 {@link #normalizeType}）。 */
    private static String normalizeItemsType(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode items = node.get("items");
        return normalizeType(items);
    }
}
