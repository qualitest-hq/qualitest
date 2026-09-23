package com.qualitest.flow.context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 模板中 {@code {{…}}} 占位符的扫描与替换。
 * <p>
 * 默认仅把以 {@code flow.}、{@code env.}、{@code asset.}、{@code http.} 开头、
 * 且前缀后还有内容的内层当作占位路径。其它 {@code {{…}}} 不当占位：只越过开头的 {@code {{}，
 * 继续向后查找，避免说明性花括号吞掉后面的真占位。
 * <p>
 * 不把反斜杠当转义；原文里的 {@code \} 按普通字符保留。
 * 写入替换结果后不再对结果做二次扫描，变量值里可含花括号。
 */
public final class MustacheScan {

    private MustacheScan() {}

    /**
     * 一次命中的占位片段。
     *
     * @param start        含定界符的起始下标
     * @param endExclusive 含定界符的结束下标（不含）
     * @param inner        定界符之间的原文（未 trim）
     */
    public record Span(int start, int endExclusive, String inner) {}

    /**
     * 判断内层是否为可求值的占位路径。
     * 先 trim，再检查是否以 flow. / env. / http. / asset. 开头且前缀后非空。
     *
     * @param inner 定界符内原文，可为 null
     * @return 是合法路径则 true
     */
    public static boolean isPlaceholderPath(String inner) {
        if (inner == null) {
            return false;
        }
        String p = inner.trim();
        return startsWithScopeAndKey(p, "flow.")
                || startsWithScopeAndKey(p, "env.")
                || startsWithScopeAndKey(p, "http.")
                || startsWithScopeAndKey(p, "asset.");
    }

    /** 字符串是否以指定前缀开头，且前缀后至少还有一个字符 */
    private static boolean startsWithScopeAndKey(String p, String prefix) {
        return p.startsWith(prefix) && p.length() > prefix.length();
    }

    /**
     * 按出现顺序收集默认规则下的占位内层（已 trim，空串丢弃）。
     *
     * @param text 模板原文
     * @return 内层路径列表；无命中则为空列表
     */
    public static List<String> listInners(String text) {
        return listInnersWhere(text, MustacheScan::isPlaceholderPath);
    }

    /**
     * 按自定义谓词收集占位内层（已 trim，空串丢弃）。
     *
     * @param text        模板原文
     * @param acceptInner 是否采纳该内层（传入未 trim 原文）
     * @return 内层路径列表
     */
    public static List<String> listInnersWhere(String text, Predicate<String> acceptInner) {
        List<String> out = new ArrayList<>();
        forEachWhere(text, acceptInner, span -> {
            String t = span.inner().trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        });
        return out;
    }

    /**
     * 按默认路径规则遍历每个占位片段。
     *
     * @param text     模板原文
     * @param consumer 命中回调
     */
    public static void forEach(String text, Consumer<Span> consumer) {
        forEachWhere(text, MustacheScan::isPlaceholderPath, consumer);
    }

    /**
     * 按自定义谓词遍历占位片段。
     * 找到 {@code {{}…{{}}} 但谓词不通过时，只前进两个字符（越过 {@code {{}），
     * 不跳到闭合 {@code }}}，以免漏掉后面的真占位。
     * 空的 {@code {{}}} 不当占位。
     *
     * @param text        模板原文
     * @param acceptInner 是否采纳该内层
     * @param consumer    命中回调
     */
    public static void forEachWhere(String text, Predicate<String> acceptInner, Consumer<Span> consumer) {
        if (text == null || text.isEmpty() || acceptInner == null || consumer == null) {
            return;
        }
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf("{{", i);
            if (open < 0) {
                return;
            }
            int close = text.indexOf("}}", open + 2);
            if (close < 0) {
                return;
            }
            if (close == open + 2) {
                i = open + 2;
                continue;
            }
            String inner = text.substring(open + 2, close);
            if (acceptInner.test(inner)) {
                consumer.accept(new Span(open, close + 2, inner));
                i = close + 2;
            } else {
                i = open + 2;
            }
        }
    }

    /**
     * 按默认路径规则替换模板中的全部占位。
     *
     * @param template 模板原文，null 视为空串
     * @param replacer 内层原文 → 替换文案；返回 null 时写入空串
     * @return 替换后的字符串
     */
    public static String replace(String template, Function<String, String> replacer) {
        return replaceWhere(template, MustacheScan::isPlaceholderPath, replacer);
    }

    /**
     * 按自定义谓词替换占位：先扫描命中片段，再拼接字面区与替换结果。
     * 字面区（含反斜杠）原样拷贝；替换结果不再扫描。
     *
     * @param template    模板原文，null 视为空串
     * @param acceptInner 是否采纳该内层
     * @param replacer    内层原文 → 替换文案；返回 null 时写入空串
     * @return 替换后的字符串
     */
    public static String replaceWhere(
            String template, Predicate<String> acceptInner, Function<String, String> replacer) {
        if (template == null) {
            return "";
        }
        if (template.isEmpty() || acceptInner == null || replacer == null) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        int[] cursor = {0};
        forEachWhere(template, acceptInner, span -> {
            out.append(template, cursor[0], span.start());
            String replacement = replacer.apply(span.inner());
            out.append(replacement != null ? replacement : "");
            cursor[0] = span.endExclusive();
        });
        out.append(template, cursor[0], template.length());
        return out.toString();
    }

    /**
     * 判断内层是否为单段简写标识（字母或下划线开头，其后为字母数字下划线，且不含点）。
     * 用于把 {@code {{token}}} 扩成完整素材占位；默认运行时求值不把它当路径。
     *
     * @param inner 定界符内原文
     * @return 是简写标识则 true
     */
    public static boolean isShortIdentifier(String inner) {
        if (inner == null) {
            return false;
        }
        String s = inner.trim();
        if (s.isEmpty() || s.indexOf('.') >= 0) {
            return false;
        }
        char c0 = s.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_')) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }
}
