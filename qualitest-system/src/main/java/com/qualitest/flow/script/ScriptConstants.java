package com.qualitest.flow.script;

/**
 * Script 节点运行时限制常量。
 * <p>
 * 含单步超时、源码体积、并发脚本数、单步 {@code ctx.http} 调用次数（{@link #MAX_HTTP_CALLS_PER_SCRIPT}）等上限。
 */
public final class ScriptConstants {

    /** 单步默认超时（毫秒） */
    public static final long DEFAULT_TIMEOUT_MS = 5000L;

    /** 单步超时上限（毫秒） */
    public static final long MAX_TIMEOUT_MS = 30000L;

    /** 单段源码最大字节数（UTF-8） */
    public static final int MAX_SOURCE_BYTES = 32768;

    /** 全局同时执行脚本的最大数量 */
    public static final int MAX_CONCURRENT_SCRIPTS = 10;

    /** 单步脚本内 ctx.http 最大调用次数 */
    public static final int MAX_HTTP_CALLS_PER_SCRIPT = 3;

    public static final String LANGUAGE_JAVASCRIPT = "javascript";

    public static final String LANGUAGE_PYTHON = "python";

    private ScriptConstants() {
    }

    /** 判断 language 字段是否为支持的脚本语言 */
    public static boolean isSupportedLanguage(String language) {
        return LANGUAGE_JAVASCRIPT.equals(language) || LANGUAGE_PYTHON.equals(language);
    }

    /** 将持久化 language 映射为 Graal 语言 id */
    public static String toGraalLanguageId(String language) {
        if (LANGUAGE_PYTHON.equals(language)) {
            return "python";
        }
        if (LANGUAGE_JAVASCRIPT.equals(language)) {
            return "js";
        }
        throw new IllegalArgumentException("不支持的脚本语言: " + language);
    }

    /** 规范化 timeoutMs：缺省默认、超出上限则截断 */
    public static long normalizeTimeoutMs(Object raw) {
        if (raw == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            long ms = (long) Double.parseDouble(String.valueOf(raw));
            if (ms <= 0) {
                return DEFAULT_TIMEOUT_MS;
            }
            return Math.min(ms, MAX_TIMEOUT_MS);
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }
}
