package com.qualitest.flow.script.tool;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 脚本工具函数 options 对象的取值；opts 可为 null，空白字符串视为未传。
 */
final class ScriptToolOptions {

    /** 工具类，不允许实例化 */
    private ScriptToolOptions() {
    }

    /** 取字符串选项；未传或空白返回 defaultValue */
    static String string(Map<String, Object> opts, String key, String defaultValue) {
        Object value = opts == null ? null : opts.get(key);
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? defaultValue : text;
    }

    /** 取字符串选项；未传返回 null，保留空串 */
    static String rawString(Map<String, Object> opts, String key) {
        Object value = opts == null ? null : opts.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 取布尔选项；未传返回 defaultValue */
    static boolean bool(Map<String, Object> opts, String key, boolean defaultValue) {
        Object value = opts == null ? null : opts.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }

    /** 取字符串集合选项；单个值按一个元素处理，未传返回 defaultValue */
    static Set<String> stringSet(Map<String, Object> opts, String key, Set<String> defaultValue) {
        Object value = opts == null ? null : opts.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Collection<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of(String.valueOf(value));
    }
}
