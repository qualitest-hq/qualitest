package com.qualitest.flow.input;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Input 节点字段类型工具：设计期校验与续跑时形态检查、写入 {@code flow}。
 * <p>
 * 合法 {@code type}：text（单行）、textarea（多行）、password（密码）、number、boolean、
 * select（单选）、multiselect（多选）、date（YYYY-MM-DD）、datetime（本地 ISO-8601）。
 * 缺省或空 type 按 text。select / multiselect 须带静态 options。
 */
public final class InputFieldTypes {

    /** 单行文本 */
    public static final String TYPE_TEXT = "text";
    /** 多行文本 */
    public static final String TYPE_TEXTAREA = "textarea";
    /** 密码；报告 assigns.after 脱敏为 *** */
    public static final String TYPE_PASSWORD = "password";
    /** 数字（Long 或 Double） */
    public static final String TYPE_NUMBER = "number";
    /** 布尔 */
    public static final String TYPE_BOOLEAN = "boolean";
    /** 单选，值须落在 options[].value */
    public static final String TYPE_SELECT = "select";
    /** 多选，值为字符串数组 */
    public static final String TYPE_MULTISELECT = "multiselect";
    /** 日期 YYYY-MM-DD */
    public static final String TYPE_DATE = "date";
    /** 日期时间，如 2026-09-06T17:00:00 */
    public static final String TYPE_DATETIME = "datetime";

    private static final Set<String> KNOWN = Set.of(
            TYPE_TEXT, TYPE_TEXTAREA, TYPE_PASSWORD, TYPE_NUMBER, TYPE_BOOLEAN,
            TYPE_SELECT, TYPE_MULTISELECT, TYPE_DATE, TYPE_DATETIME
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private InputFieldTypes() {
    }

    /**
     * 判断 type 是否合法。
     * 空或空白按 text 处理，视为合法。
     */
    public static boolean isKnownType(String type) {
        String normalized = normalizeType(type);
        return KNOWN.contains(normalized);
    }

    /**
     * 规范化 type：trim、小写；空则返回 text。
     */
    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TYPE_TEXT;
        }
        return type.trim().toLowerCase();
    }

    /**
     * 是否必须配置 options（仅 select / multiselect）。
     */
    public static boolean requiresOptions(String type) {
        String t = normalizeType(type);
        return TYPE_SELECT.equals(t) || TYPE_MULTISELECT.equals(t);
    }

    /**
     * 读取字段 required：支持 Boolean 或字符串 {@code "true"}。
     */
    public static boolean isRequired(Map<String, Object> field) {
        if (field == null) {
            return false;
        }
        Object raw = field.get("required");
        return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
    }

    /**
     * 校验人工提交的 inputs，按 type 强制转换后写入 flow。
     * <p>
     * 返回 assigns 列表，每项含 scope=flow、name、op=set、before、after（password 的 after 为 ***）。
     * 必填缺失、类型不合法、select 值不在 options 内时抛 TF_INPUT_INVALID。
     */
    public static List<Map<String, Object>> validateAndApply(
            List<Map<String, Object>> fields,
            Map<String, Object> inputs,
            Map<String, Object> flow
    ) {
        if (fields == null || fields.isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "Input 节点 fields 不能为空");
        }
        Map<String, Object> rawInputs = inputs != null ? inputs : Map.of();
        List<Map<String, Object>> assigns = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            if (field == null) {
                continue;
            }
            String name = stringVal(field.get("name"));
            if (name.isEmpty()) {
                throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "Input 字段 name 不能为空");
            }
            String type = normalizeType(stringVal(field.get("type")));
            if (!KNOWN.contains(type)) {
                throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "未知字段 type: " + type);
            }
            boolean required = isRequired(field);
            Object raw = rawInputs.containsKey(name) ? rawInputs.get(name) : null;
            if (isMissing(raw)) {
                if (required) {
                    throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "必填字段缺失: " + name);
                }
                continue;
            }
            Object coerced = coerceAndValidate(name, type, raw, field);
            Object before = flow.get(name);
            flow.put(name, coerced);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("scope", "flow");
            record.put("name", name);
            record.put("op", "set");
            record.put("before", before);
            record.put("after", TYPE_PASSWORD.equals(type) ? "***" : coerced);
            assigns.add(record);
        }
        return assigns;
    }

    /**
     * 从节点 data.fields 解析字段定义列表；非 List 或空则返回空列表。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseFields(Object raw) {
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

    /** 按 type 做形态校验并返回写入 flow 的值 */
    private static Object coerceAndValidate(String name, String type, Object raw, Map<String, Object> field) {
        return switch (type) {
            case TYPE_NUMBER -> coerceNumber(name, raw);
            case TYPE_BOOLEAN -> coerceBoolean(name, raw);
            case TYPE_SELECT -> coerceSelect(name, raw, field);
            case TYPE_MULTISELECT -> coerceMultiselect(name, raw, field);
            case TYPE_DATE -> coerceDate(name, raw);
            case TYPE_DATETIME -> coerceDateTime(name, raw);
            default -> String.valueOf(raw);
        };
    }

    private static Number coerceNumber(String name, Object raw) {
        if (raw instanceof Number n) {
            return n;
        }
        String s = String.valueOf(raw).trim();
        try {
            if (!s.contains(".") && !s.contains("e") && !s.contains("E")) {
                return Long.parseLong(s);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "字段 " + name + " 需为 number");
        }
    }

    private static Boolean coerceBoolean(String name, Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim();
        if ("true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s)) {
            return false;
        }
        throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "字段 " + name + " 需为 boolean");
    }

    private static String coerceSelect(String name, Object raw, Map<String, Object> field) {
        String value = String.valueOf(raw);
        Set<String> allowed = optionValues(field);
        if (!allowed.contains(value)) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID,
                    "字段 " + name + " 值不在 options 内: " + value);
        }
        return value;
    }

    private static List<String> coerceMultiselect(String name, Object raw, Map<String, Object> field) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof Object[] arr) {
            for (Object item : arr) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "字段 " + name + " 需为数组");
        }
        if (isRequired(field) && values.isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID, "必填多选不能为空: " + name);
        }
        Set<String> allowed = optionValues(field);
        for (String v : values) {
            if (!allowed.contains(v)) {
                throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID,
                        "字段 " + name + " 值不在 options 内: " + v);
            }
        }
        return values;
    }

    private static String coerceDate(String name, Object raw) {
        String s = String.valueOf(raw).trim();
        try {
            LocalDate.parse(s, DATE_FMT);
            return s;
        } catch (DateTimeParseException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID,
                    "字段 " + name + " 需为 YYYY-MM-DD");
        }
    }

    private static String coerceDateTime(String name, Object raw) {
        String s = String.valueOf(raw).trim();
        try {
            LocalDateTime.parse(s, DATETIME_FMT);
            return s;
        } catch (DateTimeParseException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_INPUT_INVALID,
                    "字段 " + name + " 需为 ISO-8601 本地日期时间");
        }
    }

    /** 收集 options[].value 集合，供 select / multiselect 校验 */
    private static Set<String> optionValues(Map<String, Object> field) {
        Set<String> out = new HashSet<>();
        Object raw = field.get("options");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object v = map.get("value");
                if (v != null) {
                    out.add(String.valueOf(v));
                }
            }
        }
        return out;
    }

    /** null 或空字符串视为未提交 */
    private static boolean isMissing(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String s) {
            return s.isEmpty();
        }
        return false;
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
