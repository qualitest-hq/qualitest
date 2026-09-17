package com.qualitest.flow.sync;

/**
 * 当前写库请求的变更来源（线程本地）。
 * 未设置时按 web-save 发布。
 */
public final class FlowExternalChangeSourceHolder {

    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();

    private FlowExternalChangeSourceHolder() {
    }

    /** 设置本线程写库来源 */
    public static void set(String source) {
        if (source == null || source.isBlank()) {
            SOURCE.remove();
        } else {
            SOURCE.set(source.trim());
        }
    }

    /** 读取来源；未设置返回 null */
    public static String get() {
        return SOURCE.get();
    }

    /** 读取来源，缺省为 web-save */
    public static String getOrDefault() {
        String s = SOURCE.get();
        return s != null && !s.isBlank() ? s : FlowExternalChangeEvent.SOURCE_WEB_SAVE;
    }

    /** 清除本线程来源 */
    public static void clear() {
        SOURCE.remove();
    }

    /** 在 runnable 期间临时设置来源，结束后恢复 */
    public static void runWith(String source, Runnable runnable) {
        String prev = SOURCE.get();
        try {
            set(source);
            runnable.run();
        } finally {
            if (prev == null) {
                clear();
            } else {
                set(prev);
            }
        }
    }

    /**
     * MCP 无 AI 会话；Web 全自动有会话 id。
     *
     * @param hasAiChatSession true 表示 Web 全自动路径
     */
    public static String mcpOrWebAutopilot(boolean hasAiChatSession) {
        return hasAiChatSession
                ? FlowExternalChangeEvent.SOURCE_WEB_AUTOPILOT
                : FlowExternalChangeEvent.SOURCE_MCP;
    }
}
