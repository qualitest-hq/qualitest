package com.qualitest.ai.scenario.apidesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按字段类型裁剪/校验结构约束键。
 * <p>
 * 规则概要：
 * <ul>
 *   <li>string/file/any：可用 pattern、minLength、maxLength、format</li>
 *   <li>integer/number：扁平参数用 minValue/maxValue；schema 用 minimum/maximum；
 *       另可选 exclusive*、multipleOf</li>
 *   <li>array：minItems、maxItems</li>
 *   <li>禁止 enum、const 及未知键</li>
 * </ul>
 */
public final class ApiFieldTypeConstraintSupport {

    /** 允许出现在约束 Map 中的键全集 */
    public static final Set<String> USER_CONSTRAINT_KEYS = Set.of(
            "pattern",
            "minLength",
            "maxLength",
            "format",
            "minimum",
            "maximum",
            "exclusiveMinimum",
            "exclusiveMaximum",
            "multipleOf",
            "minValue",
            "maxValue",
            "minItems",
            "maxItems"
    );

    /** 一律拒绝的废弃键 */
    private static final Set<String> DEPRECATED_KEYS = Set.of(
            "enum", "const", "enumEnabled", "constantEnabled"
    );

    private static final Set<String> STRING_KEYS = Set.of("pattern", "minLength", "maxLength", "format");
    private static final Set<String> NUMBER_SCHEMA_KEYS = Set.of(
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf"
    );
    private static final Set<String> NUMBER_FLAT_KEYS = Set.of(
            "minValue", "maxValue", "exclusiveMinimum", "exclusiveMaximum", "multipleOf"
    );
    private static final Set<String> ARRAY_KEYS = Set.of("minItems", "maxItems");

    private ApiFieldTypeConstraintSupport() {}

    /** 规范化 type 字符串（小写、去空白）；空则返回空串 */
    public static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    /** 是否允许字符串类约束（pattern/minLength 等） */
    public static boolean supportsStringConstraints(String type) {
        String t = normalizeType(type);
        return "string".equals(t) || "file".equals(t) || "any".equals(t);
    }

    /** 是否允许数值区间类约束 */
    public static boolean supportsNumberConstraints(String type) {
        String t = normalizeType(type);
        return "integer".equals(t) || "number".equals(t);
    }

    /** 是否允许数组条数约束 */
    public static boolean supportsArrayConstraints(String type) {
        return "array".equals(normalizeType(type));
    }

    /**
     * 校验并过滤约束 Map。
     * <ul>
     *   <li>废弃键、未知键 → errors</li>
     *   <li>与 type 不匹配 → 剥离并记 warnings</li>
     *   <li>flatParam=true：数值键须用 minValue/maxValue</li>
     *   <li>flatParam=false：数值键须用 minimum/maximum</li>
     * </ul>
     *
     * @param raw       原始约束
     * @param type      字段类型
     * @param flatParam true=扁平参数行，false=JSON Schema 节点
     */
    public static PruneResult pruneConstraints(Map<String, Object> raw, String type, boolean flatParam) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return new PruneResult(out, errors, warnings);
        }
        String t = normalizeType(type);
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = e.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            if (DEPRECATED_KEYS.contains(key)) {
                errors.add("禁止约束键: " + key);
                continue;
            }
            if (!USER_CONSTRAINT_KEYS.contains(key)) {
                errors.add("未知约束键: " + key);
                continue;
            }
            if (!isKeyAllowedForType(key, t, flatParam)) {
                warnings.add("已剥离与 type=" + (t.isEmpty() ? "?" : t) + " 不匹配的约束: " + key);
                continue;
            }
            if (flatParam && ("minimum".equals(key) || "maximum".equals(key))) {
                errors.add("参数位请使用 minValue/maxValue，勿用 " + key);
                continue;
            }
            if (!flatParam && ("minValue".equals(key) || "maxValue".equals(key))) {
                errors.add("schema 位请使用 minimum/maximum，勿用 " + key);
                continue;
            }
            out.put(key, e.getValue());
        }
        return new PruneResult(out, errors, warnings);
    }

    /** 判断某约束键在给定 type / 参数形态下是否允许保留 */
    private static boolean isKeyAllowedForType(String key, String type, boolean flatParam) {
        if (STRING_KEYS.contains(key)) {
            return supportsStringConstraints(type);
        }
        if (ARRAY_KEYS.contains(key)) {
            return supportsArrayConstraints(type);
        }
        if (flatParam) {
            if (NUMBER_FLAT_KEYS.contains(key)) {
                return supportsNumberConstraints(type);
            }
        } else if (NUMBER_SCHEMA_KEYS.contains(key)) {
            return supportsNumberConstraints(type);
        }
        return false;
    }

    /**
     * prune 结果容器。
     *
     * @param constraints 过滤后的合法约束
     * @param errors      阻断错误
     * @param warnings    非阻断提示
     */
    public record PruneResult(Map<String, Object> constraints, List<String> errors, List<String> warnings) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }
}
