package com.qualitest.api.result;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 脚本执行 REST 响应。
 */
@Data
@Builder
public class ApiRequestScriptExecuteResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;

    private String errorCode;

    private String errorMessage;

    @Builder.Default
    private List<String> logs = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> tests = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> writes = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> variables = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> environment = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> globals = new LinkedHashMap<>();

    private Map<String, Object> request;
}
