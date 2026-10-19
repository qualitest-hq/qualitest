package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSONObject;

/**
 * 解析 initialize 要开哪一种会话。
 * 会话模式和测试流可以写在正文 params.qualitest 里，也可以写在请求头里。
 * 有的客户端改不了 initialize 正文，所以请求头承担同一件事：X-Qualitest-Session 表示 call 或 turn，
 * X-Qualitest-Test-Flow-Id 表示回合会话绑定的测试流。两个来源都只在建立会话时读一次。
 * 正文里已经给出的字段优先；正文没给的字段再用请求头补上。
 * 两个都没给时按逐次调用。回合会话必须带测试流 id，否则不登记会话。
 */
public final class McpSessionOpen {

    private McpSessionOpen() {
    }

    /**
     * 解析结果。{@code error} 非空时不登记会话。
     *
     * @param mode       会话模式
     * @param testFlowId 回合会话绑定的测试流
     * @param error      参数错误说明
     */
    public record Parsed(McpSession.Mode mode, Long testFlowId, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    /**
     * 解析会话模式和测试流。
     * session 只能是 call 或 turn；别的值、以及 turn 却没有整数测试流 id 时，返回错误说明。
     *
     * @param request    initialize 请求，可空
     * @param modeHeader 请求头里的会话模式，可空
     * @param flowHeader 请求头里的测试流 id，可空
     */
    public static Parsed parse(JSONObject request, String modeHeader, String flowHeader) {
        JSONObject params = request == null ? null : request.getJSONObject("params");
        JSONObject qualitest = params == null ? null : params.getJSONObject("qualitest");
        String modeText = firstNonBlank(text(qualitest, "session"), modeHeader);
        String flowText = firstNonBlank(text(qualitest, "testFlowId"), flowHeader);

        McpSession.Mode mode = McpSession.Mode.CALL;
        if (modeText != null) {
            String normalized = modeText.trim();
            if ("turn".equalsIgnoreCase(normalized)) {
                mode = McpSession.Mode.TURN;
            } else if (!"call".equalsIgnoreCase(normalized)) {
                return new Parsed(null, null, "session 须为 call 或 turn");
            }
        }

        Long testFlowId = null;
        if (flowText != null && !flowText.isBlank()) {
            try {
                testFlowId = Long.parseLong(flowText.trim());
            } catch (NumberFormatException ex) {
                return new Parsed(null, null, "testFlowId 须为整数");
            }
        }
        if (mode == McpSession.Mode.TURN && testFlowId == null) {
            return new Parsed(null, null, "回合会话必须绑定 testFlowId");
        }
        return new Parsed(mode, testFlowId, null);
    }

    /** 取出对象里的非空文本；缺字段、null 或空白都当作没提供。 */
    private static String text(JSONObject object, String key) {
        if (object == null || !object.containsKey(key) || object.get(key) == null) {
            return null;
        }
        String value = String.valueOf(object.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    /** 正文有值用正文，否则用请求头。两边都空则返回 null。 */
    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
