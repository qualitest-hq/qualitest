package com.qualitest.api.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 接口资产测值：调试默认参数、请求体示例、按响应 id 存的示例等。
 * 落库列 test_value_config；与 request/response 结构列分开存。
 */
@Data
public class ApiTestValueConfig {
    /** 请求侧测值 */
    private RequestValues request;
    /** 响应侧测值 */
    private ResponseValues response;

    /** 请求测值字段 */
    @Data
    public static class RequestValues {
        /** 按参数名存的调试默认值 */
        private Map<String, Object> paramDefaults;
        /** JSON 请求体调试示例 */
        private Object bodyExample;
        /** 结构里已删掉、但仍保留默认值记录的参数名 */
        private List<String> removedParams;
    }

    /** 响应测值字段 */
    @Data
    public static class ResponseValues {
        /** 按响应条目 id 存的示例正文 */
        private Map<String, Object> examplesById;
        /** 上传合并时已删响应条目的示例归档 */
        private Map<String, Object> archivedExamples;
    }
}
