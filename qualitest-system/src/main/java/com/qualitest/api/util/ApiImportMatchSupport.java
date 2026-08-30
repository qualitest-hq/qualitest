package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.project.domain.TestProjectApi;

/**
 * API 批量导入时的接口识别与去重键生成。
 * <p>
 * 单条接口由「HTTP 方法 + apiPath」唯一确定，避免同一路径下不同方法相互覆盖。
 */
public final class ApiImportMatchSupport {

    private ApiImportMatchSupport() {
    }

    /**
     * 从 requestConfig JSON 读取 method 字段并转大写；缺省或解析失败时返回 GET。
     */
    public static String extractHttpMethod(String requestConfig) {
        if (StrUtil.isBlank(requestConfig)) {
            return "GET";
        }
        try {
            JsonNode node = ApiConfigJsonSupport.readTree(requestConfig);
            JsonNode method = node.get("method");
            if (method != null && method.isTextual() && StrUtil.isNotBlank(method.asText())) {
                return method.asText().trim().toUpperCase();
            }
        } catch (Exception ignored) {
            // 非法 JSON 时使用默认方法
        }
        return "GET";
    }

    /** 根据导入项生成批次内唯一键，格式：{@code METHOD /path} */
    public static String buildIdentity(ApiImportParams.ApiImportItem item) {
        return buildIdentity(item.getRequestConfig(), item.getApiPath());
    }

    /** 根据 requestConfig 与 apiPath 生成唯一键，格式：{@code METHOD /path} */
    public static String buildIdentity(String requestConfig, String apiPath) {
        String path = StrUtil.isBlank(apiPath) ? "/" : apiPath.trim();
        return extractHttpMethod(requestConfig) + " " + path;
    }

    /** 根据库内 API 记录生成唯一键，格式：{@code METHOD /path} */
    public static String buildIdentity(TestProjectApi api) {
        if (api == null) {
            return buildIdentity((String) null, "/");
        }
        return buildIdentity(api.getRequestConfig(), api.getApiPath());
    }

    /**
     * 是否开启上传保护：sync_protected=1 时导入整条跳过。
     */
    public static boolean isSyncProtected(TestProjectApi api) {
        return api != null && Integer.valueOf(1).equals(api.getSyncProtected());
    }

    /** 判断库内记录与导入项是否为同一路径且同一 HTTP 方法 */
    public static boolean matches(TestProjectApi api, ApiImportParams.ApiImportItem item) {
        if (api == null || item == null) {
            return false;
        }
        if (!StrUtil.equals(api.getApiPath(), item.getApiPath())) {
            return false;
        }
        return extractHttpMethod(api.getRequestConfig())
                .equalsIgnoreCase(extractHttpMethod(item.getRequestConfig()));
    }
}
