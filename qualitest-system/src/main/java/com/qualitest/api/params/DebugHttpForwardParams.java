package com.qualitest.api.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * API 调试 HTTP 转发请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugHttpForwardParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String method;
    private String url;
    private List<HeaderPair> headers;
    private Integer timeoutMs;
    private Boolean followRedirects;
    private Boolean allowInsecureTls;
    private String correlationId;
    private DebugBodySpec body;

    /**
     * 可选：项目接口 ID。
     * 有值时，转发前按接口鉴权标签与项目鉴权配置补齐缺失的托管鉴权头（已有同名头不覆盖）。
     */
    private Long testProjectApiId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderPair implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String name;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugBodySpec implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** none | raw | urlencoded | json | formData | binary */
        private String kind;
        private String raw;
        private String contentType;
        private String fileName;
        private Map<String, Object> json;
        private List<List<String>> fields;
        private List<FormFile> files;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FormFile implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;
            private String name;
            private String fileName;
            private String contentType;
            private String base64;
        }
    }
}
