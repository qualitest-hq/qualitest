package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 测试项目环境 URL 解析工具。
 * <p>
 * {@code envUrl} 支持两种存储形态：纯字符串，或多模块 JSON 对象（键为模块名，值为 base URL）。
 */
public final class EnvUrlSupport {

    /** 多模块 JSON 中优先取值的默认模块名 */
    public static final String DEFAULT_ENV_MODULE_NAME = "默认模块";

    private EnvUrlSupport() {
    }

    /**
     * 调试请求使用的 Base URL：支持纯字符串与多模块 JSON 对象两种存储。
     */
    public static String resolveEnvBaseUrlForRequest(String envUrl) {
        String raw = envUrl == null ? "" : envUrl.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("{")) {
            try {
                JSONObject o = JSON.parseObject(raw);
                if (o != null && !o.isEmpty()) {
                    String preferred = o.getString(DEFAULT_ENV_MODULE_NAME);
                    if (preferred != null && !preferred.trim().isEmpty()) {
                        return preferred.trim();
                    }
                    for (Object v : o.values()) {
                        if (v instanceof String s && !s.trim().isEmpty()) {
                            return s.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
                return "";
            }
            return "";
        }
        return raw;
    }

    /**
     * 无协议时补 {@code http://}，避免 URI 解析失败。
     */
    public static String ensureHttpSchemeForRequest(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        if (u.isEmpty()) {
            return "";
        }
        if (u.matches("(?i)^https?://.*")) {
            return u;
        }
        return "http://" + u;
    }
}
