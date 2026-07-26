package com.qualitest.flow.script;

import com.qualitest.flow.exception.FlowErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次脚本执行结果：成功时的 writes/logs，或失败时的错误码与消息。
 */
@Getter
@Builder
public class ScriptExecutionResult {

    private final boolean success;

    private final FlowErrorCode errorCode;

    private final String errorMessage;

    @Builder.Default
    private final List<Map<String, Object>> writes = new ArrayList<>();

    @Builder.Default
    private final List<String> logs = new ArrayList<>();

    public static ScriptExecutionResult ok(List<Map<String, Object>> writes, List<String> logs) {
        return ScriptExecutionResult.builder()
                .success(true)
                .writes(writes != null ? writes : List.of())
                .logs(logs != null ? logs : List.of())
                .build();
    }

    public static ScriptExecutionResult fail(FlowErrorCode code, String message) {
        return ScriptExecutionResult.builder()
                .success(false)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    /** 组装写入 step_details.script 的 Map */
    public Map<String, Object> toStepScriptDetails(String language) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("language", language);
        details.put("logs", logs);
        details.put("writes", writes);
        return details;
    }
}
