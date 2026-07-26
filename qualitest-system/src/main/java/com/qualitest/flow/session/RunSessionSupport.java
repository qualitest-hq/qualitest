package com.qualitest.flow.session;

import com.qualitest.api.params.DebugHttpForwardParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 {@link FlowRunSession} 应用到 HTTP 转发参数。
 * <p>
 * {@link #isUseRunSession} 读取节点 {@code data.useRunSession}；
 * {@link #applyToForward} 在已有 headers 上追加或覆盖 {@code Cookie} 头。
 */
public final class RunSessionSupport {

    private RunSessionSupport() {
    }

    public static boolean isUseRunSession(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return false;
        }
        Object raw = nodeData.get("useRunSession");
        if (raw instanceof Boolean b) {
            return b;
        }
        return raw != null && Boolean.parseBoolean(String.valueOf(raw));
    }

    public static void applyToForward(DebugHttpForwardParams params, FlowRunSession session) {
        if (params == null || session == null || session.isEmpty()) {
            return;
        }
        String cookie = session.buildCookieHeader();
        if (cookie.isBlank()) {
            return;
        }
        List<DebugHttpForwardParams.HeaderPair> headers = params.getHeaders() != null
                ? new ArrayList<>(params.getHeaders())
                : new ArrayList<>();
        headers.removeIf(h -> h != null && h.getName() != null
                && "cookie".equalsIgnoreCase(h.getName()));
        headers.add(new DebugHttpForwardParams.HeaderPair("Cookie", cookie));
        params.setHeaders(headers);
    }
}
