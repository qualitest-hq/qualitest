package com.qualitest.api.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.script.ScriptConstants;
import com.qualitest.flow.script.ScriptRuntime;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * API 前置/后置脚本编排服务。
 */
@Service
public class ApiRequestScriptService {

    private final ScriptRuntime scriptRuntime;
    private final IDebugHttpForwardService debugHttpForwardService;

    public ApiRequestScriptService(ScriptRuntime scriptRuntime, IDebugHttpForwardService debugHttpForwardService) {
        this.scriptRuntime = scriptRuntime;
        this.debugHttpForwardService = debugHttpForwardService;
    }

    public ApiScriptExecutionResult executePre(String source, ApiScriptContext context) {
        return execute(ApiScriptPhase.PRE, source, context);
    }

    public ApiScriptExecutionResult executePost(String source, ApiScriptContext context) {
        return execute(ApiScriptPhase.POST, source, context);
    }

    public ApiScriptExecutionResult executePreIfPresent(
            String source,
            Map<String, Object> variables,
            Map<String, Object> environment,
            Map<String, Object> globals,
            DebugHttpForwardParams forwardParams
    ) {
        ApiScriptContext context = ApiScriptSupport.create(
                ApiScriptPhase.PRE, variables, environment, globals, forwardParams, null);
        ApiScriptExecutionResult result = executePre(source, context);
        if (result.isSuccess()) {
            ApiScriptSupport.applyToForwardParams(context.getRequest(), forwardParams);
        }
        return result;
    }

    public ApiScriptExecutionResult executePostIfPresent(
            String source,
            Map<String, Object> variables,
            Map<String, Object> environment,
            Map<String, Object> globals,
            DebugHttpForwardParams forwardParams,
            ApiScriptContext.ResponseSnapshot responseSnapshot
    ) {
        ApiScriptContext context = ApiScriptSupport.create(
                ApiScriptPhase.POST, variables, environment, globals, forwardParams, responseSnapshot);
        return executePost(source, context);
    }

    private ApiScriptExecutionResult execute(ApiScriptPhase phase, String source, ApiScriptContext context) {
        context.setPhase(phase);
        ApiScriptExecutionResult result = scriptRuntime.executeApiScript(
                phase, source, context, ScriptConstants.DEFAULT_TIMEOUT_MS, debugHttpForwardService);
        if (result.isSuccess() && context.isTestsFailed()) {
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_ASSERT_FAILED,
                    "后置脚本断言未通过",
                    context
            );
        }
        return result;
    }
}
