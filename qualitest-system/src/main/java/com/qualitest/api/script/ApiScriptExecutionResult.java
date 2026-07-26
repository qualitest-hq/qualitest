package com.qualitest.api.script;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.script.ScriptConstants;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 脚本单次执行结果。
 */
@Getter
@Builder
public class ApiScriptExecutionResult {

    private final boolean success;

    private final FlowErrorCode errorCode;

    private final String errorMessage;

    @Builder.Default
    private final ApiScriptContext context = new ApiScriptContext();

    @Builder.Default
    private final List<String> logs = new ArrayList<>();

    @Builder.Default
    private final List<Map<String, Object>> tests = new ArrayList<>();

    @Builder.Default
    private final List<Map<String, Object>> writes = new ArrayList<>();

    public static ApiScriptExecutionResult ok(ApiScriptContext ctx) {
        return ApiScriptExecutionResult.builder()
                .success(true)
                .context(ctx)
                .logs(ctx != null ? new ArrayList<>(ctx.getLogs()) : List.of())
                .tests(ctx != null ? new ArrayList<>(ctx.getTests()) : List.of())
                .writes(ctx != null ? new ArrayList<>(ctx.getWrites()) : List.of())
                .build();
    }

    public static ApiScriptExecutionResult skip(ApiScriptContext ctx) {
        return ok(ctx);
    }

    public static ApiScriptExecutionResult fail(FlowErrorCode code, String message, ApiScriptContext ctx) {
        return ApiScriptExecutionResult.builder()
                .success(false)
                .errorCode(code)
                .errorMessage(message)
                .context(ctx)
                .logs(ctx != null ? new ArrayList<>(ctx.getLogs()) : List.of())
                .tests(ctx != null ? new ArrayList<>(ctx.getTests()) : List.of())
                .writes(ctx != null ? new ArrayList<>(ctx.getWrites()) : List.of())
                .build();
    }

    public Map<String, Object> toStepScriptDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("language", ScriptConstants.LANGUAGE_JAVASCRIPT);
        details.put("logs", logs);
        details.put("writes", writes);
        details.put("tests", tests);
        return details;
    }
}
