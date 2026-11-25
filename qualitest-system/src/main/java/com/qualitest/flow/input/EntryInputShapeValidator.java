package com.qualitest.flow.input;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP 入口 {@code data.inputShape} 运行时校验。
 * <p>
 * 对 body / query / headers 三节声明做必填与类型检查；body 字段若带 {@code children} 则递归进子对象。
 * shape 为空或三节都无字段时不校验。不符时返回可读错误文案，开放入站据此返回 400。
 */
public final class EntryInputShapeValidator {

    /** 开放入站：入参形状不符时的平台错误码 */
    public static final String ERROR_CODE = "OPEN_INPUT_SHAPE_MISMATCH";

    private EntryInputShapeValidator() {
    }

    /**
     * 校验请求是否满足入口声明的 inputShape。
     *
     * @param inputShape 入口 data.inputShape，可空
     * @param query      查询参数
     * @param headers    请求头
     * @param body       请求体（有 body 字段声明时须为 JSON 对象）
     * @return 失败原因；通过或无需校验时为 empty
     */
    public static Optional<String> validate(
            Map<String, Object> inputShape,
            Map<String, Object> query,
            Map<String, String> headers,
            Object body
    ) {
        if (inputShape == null || inputShape.isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> bodyFields = parseFieldList(inputShape.get("body"));
        List<Map<String, Object>> queryFields = parseFieldList(inputShape.get("query"));
        List<Map<String, Object>> headerFields = parseFieldList(inputShape.get("headers"));
        if (bodyFields.isEmpty() && queryFields.isEmpty() && headerFields.isEmpty()) {
            return Optional.empty();
        }
        if (!bodyFields.isEmpty() && body != null && !(body instanceof Map<?, ?>)) {
            return Optional.of("body 须为 JSON 对象");
        }
        Optional<String> err = validateSection("body", bodyFields, asStringKeyMap(body));
        if (err.isPresent()) {
            return err;
        }
        err = validateSection("query", queryFields, query != null ? query : Map.of());
        if (err.isPresent()) {
            return err;
        }
        Map<String, Object> headerVals = new java.util.LinkedHashMap<>();
        if (headers != null) {
            headerVals.putAll(headers);
        }
        err = validateSection("headers", headerFields, headerVals);
        return err;
    }

    /**
     * 校验单个节：body 允许 children 递归；query / headers 只扫一层。
     * headers 节取值时字段名忽略大小写。
     *
     * @param section 节名（写入错误文案路径前缀）
     * @param fields  该节声明的字段列表
     * @param values  该节实际取值
     * @return 首个失败原因；全部通过时为 empty
     */
    private static Optional<String> validateSection(
            String section,
            List<Map<String, Object>> fields,
            Map<String, Object> values
    ) {
        return validateFields(section, fields, values, "body".equals(section));
    }

    /**
     * 逐字段检查必填与类型。
     * {@code allowNested=true} 时：字段若声明了 children，实际值须为对象（必填时），并递归校验子键。
     *
     * @param pathPrefix  错误文案路径前缀，如 body、body.order
     * @param fields      本层字段声明
     * @param values      本层实际取值 Map
     * @param allowNested 是否解析并校验 children
     */
    private static Optional<String> validateFields(
            String pathPrefix,
            List<Map<String, Object>> fields,
            Map<String, Object> values,
            boolean allowNested
    ) {
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        for (Map<String, Object> field : fields) {
            if (field == null) {
                continue;
            }
            String name = stringVal(field.get("name"));
            if (name.isEmpty()) {
                continue;
            }
            String type = normalizeShapeType(stringVal(field.get("type")));
            boolean required = InputFieldTypes.isRequired(field);
            // 路径落在 headers 节（含嵌套前缀以 headers 结尾）时按忽略大小写取头
            Object raw = lookupValue(values, name, pathPrefix.endsWith("headers") || "headers".equals(pathPrefix));
            String fieldPath = pathPrefix + "." + name;
            if (isMissing(raw)) {
                if (required) {
                    return Optional.of(pathPrefix + " 缺少必填字段: " + name);
                }
                continue;
            }
            if (!valueMatchesType(type, raw)) {
                return Optional.of(pathPrefix + " 字段类型不符: " + name);
            }
            if (!allowNested) {
                continue;
            }
            List<Map<String, Object>> children = parseFieldList(field.get("children"));
            if (children.isEmpty()) {
                continue;
            }
            // 有子声明但实际值不是对象：必填则报错，非必填则跳过子校验
            if (!(raw instanceof Map<?, ?>)) {
                if (required) {
                    return Optional.of(fieldPath + " 须为对象以校验子字段");
                }
                continue;
            }
            Optional<String> childErr = validateFields(fieldPath, children, asStringKeyMap(raw), true);
            if (childErr.isPresent()) {
                return childErr;
            }
        }
        return Optional.empty();
    }

    /** 把节声明解析为字段 Map 列表，非列表或空时返回空列表，列表内非 Map 元素被忽略 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseFieldList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    /** 把请求体转为字符串键 Map，非 Map 请求体返回空 Map */
    private static Map<String, Object> asStringKeyMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    /**
     * 按字段名取值：优先精确匹配，必要时再按不区分大小写匹配。
     *
     * @param values          取值表
     * @param name            字段名
     * @param caseInsensitive 精确匹配未命中时是否允许忽略大小写查找
     * @return 字段值；未找到返回 null
     */
    private static Object lookupValue(Map<String, Object> values, String name, boolean caseInsensitive) {
        if (values == null || values.isEmpty() || name.isEmpty()) {
            return null;
        }
        if (values.containsKey(name)) {
            return values.get(name);
        }
        if (!caseInsensitive) {
            return null;
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getKey() != null && name.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 规范化声明类型：空→text；string 别名→text；object/json 原样保留；其余走 InputFieldTypes。
     */
    private static String normalizeShapeType(String type) {
        if (type == null || type.isBlank()) {
            return InputFieldTypes.TYPE_TEXT;
        }
        String t = type.trim().toLowerCase();
        if ("string".equals(t)) {
            return InputFieldTypes.TYPE_TEXT;
        }
        if ("object".equals(t) || "json".equals(t)) {
            return t;
        }
        return InputFieldTypes.normalizeType(type);
    }

    /**
     * 实际值是否满足声明类型。
     * object/json 须为 Map；number/boolean/multiselect 有专门判定；其余有值即过。
     */
    private static boolean valueMatchesType(String type, Object raw) {
        if (raw == null) {
            return false;
        }
        String t = type == null ? "" : type.trim().toLowerCase();
        if ("object".equals(t) || "json".equals(t)) {
            return raw instanceof Map<?, ?>;
        }
        return switch (type) {
            case InputFieldTypes.TYPE_NUMBER -> raw instanceof Number || isNumericString(raw);
            case InputFieldTypes.TYPE_BOOLEAN ->
                    raw instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(raw))
                            || "false".equalsIgnoreCase(String.valueOf(raw));
            case InputFieldTypes.TYPE_MULTISELECT -> raw instanceof Collection<?> || raw.getClass().isArray();
            default -> true;
        };
    }

    /** 判断是否为可解析为数字的非空字符串（整数或浮点） */
    private static boolean isNumericString(Object raw) {
        if (!(raw instanceof String s) || s.isBlank()) {
            return false;
        }
        try {
            if (!s.contains(".") && !s.contains("e") && !s.contains("E")) {
                Long.parseLong(s.trim());
            } else {
                Double.parseDouble(s.trim());
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 判断值是否缺失：null 或空字符串视为缺失 */
    private static boolean isMissing(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String s) {
            return s.isEmpty();
        }
        return false;
    }

    /** 转为去首尾空白的字符串，null 返回空串 */
    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
