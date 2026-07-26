package com.qualitest.flow.context;

/**
 * 根据测试环境 URL 计算被测系统数据快照服务的根地址。
 * <p>
 * 质衡不单独存储 reset 端点配置，运行时由 envUrl 拼出 {@code /test-support} 路径。
 * 被测系统在该路径下提供 snapshot、restore 接口；质衡在节点 checkpoint 时调用。
 */
public final class ResetEndpointSupport {

    /** 被测系统快照服务挂载的默认路径段 */
    public static final String TEST_SUPPORT_PATH = "/test-support";

    private ResetEndpointSupport() {
    }

    /**
     * 解析快照服务根地址。
     *
     * @param envUrl 环境 URL，支持纯字符串或多模块 JSON（取默认模块的 baseUrl）
     * @return 完整根地址，例如 http://host:8081/test-support；无法解析时返回空串
     */
    public static String resolve(String envUrl) {
        String base = EnvUrlSupport.resolveEnvBaseUrlForRequest(envUrl);
        if (base.isEmpty()) {
            return "";
        }
        base = EnvUrlSupport.ensureHttpSchemeForRequest(base);
        return joinBaseAndPath(base, TEST_SUPPORT_PATH);
    }

    /**
     * 拼接 baseUrl 与路径，去掉 base 末尾斜杠，保证 path 以 / 开头。
     */
    static String joinBaseAndPath(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String p = path == null ? "" : path.trim();
        if (base.isEmpty()) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }
}
