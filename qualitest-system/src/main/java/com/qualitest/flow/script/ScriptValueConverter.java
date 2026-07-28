package com.qualitest.flow.script;

import com.alibaba.fastjson2.JSON;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本与 Java 之间交换值的类型转换器。
 * <p>
 * 写入 flow 与返回给脚本的值限定为 JSON 友好类型：String / Number / Boolean / Map / List / null。
 * 在 {@code HostAccess.NONE} 下，返回给脚本的 Map/List 须包装为 {@link ProxyObject}/{@link ProxyArray}，
 * 否则 guest 无法用点语法或下标读取。
 */
public final class ScriptValueConverter {

    private ScriptValueConverter() {
    }

    /** 将脚本侧传入的值转为可写入 flow 的 Java 对象 */
    public static Object fromGuest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return normalizeNumber(number);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), fromGuest(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(fromGuest(item));
            }
            return out;
        }
        return String.valueOf(value);
    }

    /**
     * 将 flow / env / asset 中的 Java 值转为可落库/可记录的 JSON 友好对象（仍为 Map/List）。
     * 若需返回给脚本语言，请用 {@link #toScriptValue(Object)}。
     */
    public static Object toGuest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return normalizeNumber(number);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), toGuest(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toGuest(item));
            }
            return out;
        }
        return String.valueOf(value);
    }

    /**
     * 将 Java 值转为脚本可读对象：标量原样返回，Map/List 递归包装为 Proxy。
     */
    public static Object toScriptValue(Object value) {
        return wrapForScript(toGuest(value));
    }

    /** 解析 JSON 字符串为脚本可读结构（Map/List 已包 Proxy） */
    public static Object jsonParse(String text) {
        if (text == null) {
            return null;
        }
        Object parsed = JSON.parse(text);
        return toScriptValue(fromGuest(parsed));
    }

    /** 将 Java 值序列化为 JSON 字符串 */
    public static String jsonStringify(Object value) {
        return JSON.toJSONString(toGuest(value));
    }

    private static Object wrapForScript(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), wrapForScript(entry.getValue()));
            }
            return ProxyObject.fromMap(out);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(wrapForScript(item));
            }
            return ProxyArray.fromList(out);
        }
        return value;
    }

    private static Number normalizeNumber(Number number) {
        double d = number.doubleValue();
        if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) <= Long.MAX_VALUE) {
            return (long) d;
        }
        return d;
    }
}
