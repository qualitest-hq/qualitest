package com.qualitest.flow.script;

import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * 脚本宿主函数的参数校验与取值：必填 / 可选字符串、对象、options。
 * 字符串参数接受数字 / 布尔并按文本处理。
 */
public final class ScriptArgs {

    /** 工具类，不允许实例化 */
    private ScriptArgs() {
    }

    /** 参数个数不足时抛出「name 参数不足」 */
    public static void requireArgs(Value[] args, int min, String name) {
        if (args == null || args.length < min) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
    }

    /** 将参数转为 JSON 友好的 Java 值（数字已归一） */
    public static Object toJava(Value value) {
        return ScriptValueConverter.fromGuest(ScriptValueConverter.guestToJava(value));
    }

    /** 必填字符串参数 */
    public static String str(Value[] args, int index, String name) {
        if (isMissing(args, index)) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
        return asText(args[index]);
    }

    /** 可选字符串参数；缺省或 null 返回 null */
    public static String optStr(Value[] args, int index) {
        return isMissing(args, index) ? null : asText(args[index]);
    }

    /** 必填对象参数 */
    public static Map<String, Object> object(Value[] args, int index, String name) {
        if (isMissing(args, index)) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
        return asMap(toJava(args[index]), name + " 参数必须是对象");
    }

    /** 可选 options 对象；缺省返回空 Map */
    public static Map<String, Object> opts(Value[] args, int index, String name) {
        if (isMissing(args, index)) {
            return Map.of();
        }
        return asMap(toJava(args[index]), name + " 的 options 必须是对象");
    }

    /** 第 index 个参数未传或为 null / None */
    private static boolean isMissing(Value[] args, int index) {
        return args == null || args.length <= index || args[index] == null || args[index].isNull();
    }

    /** 参数转文本：字符串原样返回，数字 / 布尔等转字符串（整数不带 .0） */
    private static String asText(Value value) {
        return value.isString() ? value.asString() : String.valueOf(toJava(value));
    }

    /** 校验为对象并转为 Map，否则抛出给定错误信息 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String error) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(error);
    }
}
