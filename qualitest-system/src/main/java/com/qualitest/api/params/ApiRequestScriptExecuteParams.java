package com.qualitest.api.params;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 前置/后置脚本执行请求。
 */
@Data
public class ApiRequestScriptExecuteParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String source;

    private Map<String, Object> variables = new LinkedHashMap<>();

    private Map<String, Object> environment = new LinkedHashMap<>();

    private Map<String, Object> globals = new LinkedHashMap<>();

    private ApiRequestSnapshot request = new ApiRequestSnapshot();

    private ApiResponseSnapshot response;

    @Data
    public static class ApiRequestSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String method;
        private String url;
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, Object> body = new LinkedHashMap<>();
    }

    @Data
    public static class ApiResponseSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Integer status;
        private Integer code;
        private String statusText;
        private Map<String, Object> headers = new LinkedHashMap<>();
        private String bodyText;
        private Long durationMs;
    }
}
