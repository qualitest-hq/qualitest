package com.qualitest.api.util;

/**
 * 上传包 requestConfig / responseConfig 落库前的预处理入口。
 * <p>
 * 先规范化字段结构（补齐缺省键、校验 configVersion），再按 schema 补全缺失的 example。
 * 新增全量写入与更新合并前都会走这里，保证入库 JSON 形态统一。
 */
public final class ApiImportConfigPipeline {

    private ApiImportConfigPipeline() {
    }

    /**
     * 规范化 requestConfig，并为 body/参数侧补全缺失的 example。
     *
     * @param raw 上传包中的 requestConfig 原始字符串，可为 null
     * @return 规范化并补全后的 JSON 字符串
     */
    public static String normalizeAndEnrichRequest(String raw) {
        return ApiConfigExampleEnricher.enrichRequestConfig(RequestConfigImportNormalizer.normalize(raw));
    }

    /**
     * 规范化 responseConfig，并为 contentType=json 的响应项补全缺失的 example。
     *
     * @param raw 上传包中的 responseConfig 原始字符串，可为 null
     * @return 规范化并补全后的 JSON 字符串
     */
    public static String normalizeAndEnrichResponse(String raw) {
        return ApiConfigExampleEnricher.enrichResponseConfig(ResponseConfigImportNormalizer.normalize(raw));
    }
}
