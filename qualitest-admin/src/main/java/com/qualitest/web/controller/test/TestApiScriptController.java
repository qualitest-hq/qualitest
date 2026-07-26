package com.qualitest.web.controller.test;

import com.qualitest.api.params.ApiRequestScriptExecuteParams;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.ApiRequestScriptExecuteResult;
import com.qualitest.api.script.ApiRequestScriptService;
import com.qualitest.api.script.ApiScriptContext;
import com.qualitest.api.script.ApiScriptExecutionResult;
import com.qualitest.api.script.ApiScriptPhase;
import com.qualitest.api.script.ApiScriptSupport;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 调试前置/后置脚本执行（需登录 JWT）。
 */
@Slf4j
@RestController
@RequestMapping("/test/api-request-script")
@AllArgsConstructor
public class TestApiScriptController {

    private final ApiRequestScriptService apiRequestScriptService;

    @PostMapping("/pre")
    public ApiRequestScriptExecuteResult executePre(@RequestBody ApiRequestScriptExecuteParams params) {
        ApiScriptContext context = ApiScriptSupport.toContext(ApiScriptPhase.PRE, params);
        DebugHttpForwardParams forwardParams = ApiScriptSupport.toForwardParams(
                params != null ? params.getRequest() : null);
        ApiScriptExecutionResult result = apiRequestScriptService.executePre(
                params != null ? params.getSource() : null,
                context
        );
        if (result.isSuccess()) {
            ApiScriptSupport.applyToForwardParams(context.getRequest(), forwardParams);
            context.setRequest(ApiScriptSupport.fromForwardParams(forwardParams));
        }
        return ApiScriptSupport.toResult(result);
    }

    @PostMapping("/post")
    public ApiRequestScriptExecuteResult executePost(@RequestBody ApiRequestScriptExecuteParams params) {
        ApiScriptContext context = ApiScriptSupport.toContext(ApiScriptPhase.POST, params);
        ApiScriptExecutionResult result = apiRequestScriptService.executePost(
                params != null ? params.getSource() : null,
                context
        );
        return ApiScriptSupport.toResult(result);
    }
}
