package com.qualitest.flow.contract;

import com.qualitest.flow.delivery.HttpStatusCodes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 入口（http-entry）专用默认值与 fallbackOut 形状检查。
 * <p>
 * 新建入口、面板回退、校验报错共用本类；不处理子流入口的业务对象形兜底。
 */
public final class HttpEntryDefaults {

    /** 未配置时写入 fallbackOut.status 的默认 HTTP 状态码 */
    public static final int DEFAULT_FALLBACK_HTTP_STATUS = 500;

    /** 默认兜底响应头 Content-Type */
    public static final String DEFAULT_FALLBACK_CONTENT_TYPE = "application/json;charset=UTF-8";

    private HttpEntryDefaults() {
    }

    /**
     * 构造 HTTP 入口完整 fallbackOut：含 status、headers、body。
     * body 使用零次返回业务对象。
     */
    public static Map<String, Object> defaultFallbackOut() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", DEFAULT_FALLBACK_CONTENT_TYPE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", DEFAULT_FALLBACK_HTTP_STATUS);
        out.put("headers", headers);
        out.put("body", FlowContractDefaults.defaultNoReturnBody());
        return out;
    }

    /**
     * 判断 HTTP fallbackOut 是否完整可用：status 可解析为 100–599，且同时含 headers、body 键。
     */
    public static boolean isCompleteFallbackOut(Map<?, ?> fb) {
        if (fb == null) {
            return false;
        }
        if (HttpStatusCodes.coerce(fb.get("status")) == null) {
            return false;
        }
        return fb.containsKey("headers") && fb.containsKey("body");
    }

    /**
     * 把 HTTP fallbackOut 形状问题追加到 errors（status / headers / body）。
     * 调用方负责先判断「是否配置了 fallbackOut」本身。
     *
     * @param label  入口展示名，写入报错文案
     * @param fb     待检查对象，可为 null（本方法直接返回）
     * @param errors 收集错误文案的列表
     */
    public static void appendFallbackOutShapeErrors(String label, Map<?, ?> fb, List<String> errors) {
        if (fb == null) {
            return;
        }
        if (HttpStatusCodes.coerce(fb.get("status")) == null) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut.status 须为 100–599 的数字");
        }
        if (!fb.containsKey("headers")) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut 须含 headers");
        }
        if (!fb.containsKey("body")) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut 须含 body");
        }
    }
}
