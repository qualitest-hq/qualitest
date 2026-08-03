package com.qualitest.flow.node.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.script.ApiRequestScriptService;
import com.qualitest.api.script.ApiScriptContext;
import com.qualitest.api.script.ApiScriptExecutionResult;
import com.qualitest.api.script.ApiScriptSupport;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.ExtractApplicator;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.http.HttpStepDetailsDesensitizer;
import com.qualitest.flow.http.SuccessCheckResolver;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.session.RunSessionSupport;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 节点执行器（type=http）。
 * <p>
 * 按节点 data.callMode 分两条链路，共用 HTTP 转发与 extracts 抽变量：
 * <ul>
 *   <li><b>project</b> — 绑定 testProjectApiId，合并项目接口资产；pre/post 脚本来自接口定义</li>
 *   <li><b>external</b> — 使用节点 externalUrl / httpMethod / headers / requestBody；
 *       运行前校验外联权限与 URL；pre/post 脚本来自节点 data；步骤报告会脱敏敏感字段</li>
 * </ul>
 * 可选 useRunSession=true：在 Run 级 Cookie Jar 中注入与吸收 Cookie。
 * <p>
 * 成功判定顺序：
 * <ol>
 *   <li>HTTP 状态码非 2xx → 步骤失败，错误码 TF_HTTP_STATUS</li>
 *   <li>2xx 后若开启业务码校验（节点 successCheck.mode 非 off）→ 读取 body 中业务码；
 *       不在成功白名单内则步骤失败，错误码 TF_BIZ_CODE，并在步骤 http.bizCheck 写入实际码与消息</li>
 * </ol>
 * 通过后写入 lastResponse，再执行 extracts。
 */
@Component
public class HttpNodeHandler extends AbstractStubNodeHandler {

    private final ITestProjectApiService testProjectApiService;
    private final IDebugHttpForwardService debugHttpForwardService;
    private final ApiRequestScriptService apiRequestScriptService;

    public HttpNodeHandler(ITestProjectApiService testProjectApiService,
                           IDebugHttpForwardService debugHttpForwardService,
                           ApiRequestScriptService apiRequestScriptService) {
        super(FlowNodeType.HTTP);
        this.testProjectApiService = testProjectApiService;
        this.debugHttpForwardService = debugHttpForwardService;
        this.apiRequestScriptService = apiRequestScriptService;
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        String nodeName = resolveNodeName(node);
        String callMode = resolveCallMode(data);

        try {
            if (FlowHttpCallMode.isExternal(callMode)) {
                return executeExternal(ctx, node, incomingEdgeId, nodeName, data, t0);
            }
            return executeProject(ctx, node, incomingEdgeId, nodeName, data, t0);
        } catch (FlowExecutionException e) {
            return failed(node, incomingEdgeId, nodeName, System.currentTimeMillis() - t0, ctx,
                    e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            return failed(node, incomingEdgeId, nodeName, System.currentTimeMillis() - t0, ctx,
                    FlowErrorCode.TF_STEP_ERROR, e.getMessage() != null ? e.getMessage() : "HTTP 步骤异常");
        }
    }

    private StepResult executeProject(FlowRunContext ctx, GraphNode node, String incomingEdgeId,
                                      String nodeName, Map<String, Object> data, long t0) {
        Long apiId = parseApiId(data.get("testProjectApiId"));
        TestProjectApi api = testProjectApiService.selectTestProjectApiById(apiId);
        if (api == null) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    FlowErrorCode.TF_HTTP_UNBOUND, "API 不存在: " + apiId);
        }
        // 叠 paramDefaults / 响应 example 等有效配置；跑流 body 不退回接口默认（见 FlowHttpRequestBuilder）
        TestProjectApi effectiveApi = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromProject(ctx, effectiveApi, data);

        ApiScriptExecutionResult preScriptResult = null;
        if (effectiveApi.getPreRequestScript() != null && !effectiveApi.getPreRequestScript().isBlank()) {
            preScriptResult = apiRequestScriptService.executePreIfPresent(
                    effectiveApi.getPreRequestScript(),
                    ctx.getFlow(),
                    ctx.getEnv(),
                    Map.of(),
                    built.getForwardParams()
            );
            if (!preScriptResult.isSuccess()) {
                return scriptFailed(node, incomingEdgeId, nodeName, t0, ctx, preScriptResult, "preScript");
            }
        }

        if (RunSessionSupport.isUseRunSession(data)) {
            RunSessionSupport.applyToForward(built.getForwardParams(), ctx.getRunSession());
        }

        DebugHttpForwardResult forwardResult = debugHttpForwardService.forward(built.getForwardParams());
        long durationMs = System.currentTimeMillis() - t0;

        if (!forwardResult.isForwarded()) {
            return failed(node, incomingEdgeId, nodeName, durationMs, ctx,
                    FlowErrorCode.TF_STEP_ERROR,
                    forwardResult.getError() != null ? forwardResult.getError() : "HTTP 转发被拒绝");
        }
        if (forwardResult.getError() != null && forwardResult.getErrorCode() != null) {
            FlowErrorCode code = mapForwardError(forwardResult.getErrorCode());
            return failed(node, incomingEdgeId, nodeName, durationMs, ctx, code, forwardResult.getError());
        }

        int status = forwardResult.getStatus() != null ? forwardResult.getStatus() : 0;
        Map<String, Object> responseHeaders = toObjectHeaders(forwardResult.getResponseHeaders());
        if (RunSessionSupport.isUseRunSession(data)) {
            ctx.getRunSession().absorbSetCookieHeaders(forwardResult.getResponseHeaders());
        }
        Object body = parseResponseBody(forwardResult.getBodyText());

        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(body)
                .durationMs(durationMs)
                .build();
        ctx.setLastResponse(snapshot);

        ApiScriptExecutionResult postScriptResult = null;
        if (effectiveApi.getPostRequestScript() != null && !effectiveApi.getPostRequestScript().isBlank()) {
            ApiScriptContext.ResponseSnapshot responseSnapshot = ApiScriptSupport.fromForwardResult(
                    status,
                    forwardResult.getStatusText(),
                    forwardResult.getResponseHeaders(),
                    forwardResult.getBodyText(),
                    durationMs
            );
            postScriptResult = apiRequestScriptService.executePostIfPresent(
                    effectiveApi.getPostRequestScript(),
                    ctx.getFlow(),
                    ctx.getEnv(),
                    Map.of(),
                    built.getForwardParams(),
                    responseSnapshot
            );
            if (!postScriptResult.isSuccess()) {
                return scriptFailed(node, incomingEdgeId, nodeName, t0, ctx, postScriptResult, "postScript");
            }
        }

        return buildHttpStepResult(ctx, node, incomingEdgeId, nodeName, data, built, durationMs,
                status, responseHeaders, body, preScriptResult, postScriptResult, effectiveApi);
    }

    private StepResult executeExternal(FlowRunContext ctx, GraphNode node, String incomingEdgeId,
                                       String nodeName, Map<String, Object> data, long t0) {
        if (!ctx.isExternalHttpPermitted()) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "当前用户无 flow:http:external 权限");
        }

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromExternal(ctx, data);

        ApiScriptExecutionResult preScriptResult = null;
        String preScript = stringOrNull(data.get("preScript"));
        if (preScript != null) {
            preScriptResult = apiRequestScriptService.executePreIfPresent(
                    preScript, ctx.getFlow(), ctx.getEnv(), Map.of(), built.getForwardParams());
            if (!preScriptResult.isSuccess()) {
                return scriptFailed(node, incomingEdgeId, nodeName, t0, ctx, preScriptResult, "preScript");
            }
        }

        if (RunSessionSupport.isUseRunSession(data)) {
            RunSessionSupport.applyToForward(built.getForwardParams(), ctx.getRunSession());
        }

        DebugHttpForwardResult forwardResult = debugHttpForwardService.forward(built.getForwardParams());
        long durationMs = System.currentTimeMillis() - t0;

        if (!forwardResult.isForwarded()) {
            return failed(node, incomingEdgeId, nodeName, durationMs, ctx,
                    FlowErrorCode.TF_STEP_ERROR,
                    forwardResult.getError() != null ? forwardResult.getError() : "HTTP 转发被拒绝");
        }
        if (forwardResult.getError() != null && forwardResult.getErrorCode() != null) {
            FlowErrorCode code = mapForwardError(forwardResult.getErrorCode());
            return failed(node, incomingEdgeId, nodeName, durationMs, ctx, code, forwardResult.getError());
        }

        int status = forwardResult.getStatus() != null ? forwardResult.getStatus() : 0;
        Map<String, Object> responseHeaders = toObjectHeaders(forwardResult.getResponseHeaders());
        if (RunSessionSupport.isUseRunSession(data)) {
            ctx.getRunSession().absorbSetCookieHeaders(forwardResult.getResponseHeaders());
        }
        Object body = parseResponseBody(forwardResult.getBodyText());

        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(body)
                .durationMs(durationMs)
                .build();
        ctx.setLastResponse(snapshot);

        ApiScriptExecutionResult postScriptResult = null;
        String postScript = stringOrNull(data.get("postScript"));
        if (postScript != null) {
            ApiScriptContext.ResponseSnapshot responseSnapshot = ApiScriptSupport.fromForwardResult(
                    status,
                    forwardResult.getStatusText(),
                    forwardResult.getResponseHeaders(),
                    forwardResult.getBodyText(),
                    durationMs
            );
            postScriptResult = apiRequestScriptService.executePostIfPresent(
                    postScript, ctx.getFlow(), ctx.getEnv(), Map.of(),
                    built.getForwardParams(), responseSnapshot);
            if (!postScriptResult.isSuccess()) {
                return scriptFailed(node, incomingEdgeId, nodeName, t0, ctx, postScriptResult, "postScript");
            }
        }

        return buildHttpStepResult(ctx, node, incomingEdgeId, nodeName, data, built, durationMs,
                status, responseHeaders, body, preScriptResult, postScriptResult, null);
    }

    private StepResult buildHttpStepResult(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data,
            FlowHttpRequestBuilder.BuiltHttpRequest built, long durationMs,
            int status, Map<String, Object> responseHeaders, Object body,
            ApiScriptExecutionResult preScriptResult,
            ApiScriptExecutionResult postScriptResult,
            TestProjectApi api) {
        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(body)
                .durationMs(durationMs)
                .build();

        JSONArray extractsConfig = toExtractsArray(data.get("extracts"));
        List<JSONObject> appliedExtracts = ExtractApplicator.apply(extractsConfig, ctx, snapshot);

        Map<String, Object> httpDetails = new LinkedHashMap<>();
        httpDetails.put("callMode", built.getCallMode());
        httpDetails.put("method", built.getMethod());
        httpDetails.put("url", built.getUrl());
        httpDetails.put("status", status);
        httpDetails.put("request", built.getRequestSnapshot());
        Map<String, Object> responseSnapshot = new LinkedHashMap<>();
        responseSnapshot.put("status", status);
        responseSnapshot.put("headers", responseHeaders);
        responseSnapshot.put("body", body);
        httpDetails.put("response", responseSnapshot);
        httpDetails.put("durationMs", durationMs);
        if (preScriptResult != null) {
            httpDetails.put("preScript", preScriptResult.toStepScriptDetails());
        }
        if (postScriptResult != null) {
            httpDetails.put("postScript", postScriptResult.toStepScriptDetails());
        }
        if (FlowHttpCallMode.isExternal(built.getCallMode())) {
            httpDetails = HttpStepDetailsDesensitizer.desensitize(httpDetails);
        }
        if (RunSessionSupport.isUseRunSession(data) && ctx.getRunSession() != null && !ctx.getRunSession().isEmpty()) {
            httpDetails.put("runSessionCookies", ctx.getRunSession().snapshot());
        }

        // HTTP 状态码不在 2xx：传输层失败
        if (status < 200 || status >= 300) {
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.HTTP.getCode())
                    .nodeName(nodeName)
                    .edgeId(incomingEdgeId)
                    .status(StepResult.STATUS_FAILED)
                    .durationMs(durationMs)
                    .http(httpDetails)
                    .extracts(appliedExtracts)
                    .flowAfter(copyFlow(ctx))
                    .error(StepError.of(FlowErrorCode.TF_HTTP_STATUS, "HTTP " + status))
                    .build();
        }

        // HTTP 2xx 之后：按节点 successCheck 决定是否校验 body 业务码
        // mode=off 或外联默认关闭时跳过；开启时用项目约定（或接口白名单）判定成功值
        SuccessCheckResolver.Resolved check = SuccessCheckResolver.resolve(
                data, built.getCallMode(), api, ctx.getResponseConvention());
        if (check.shouldApply()) {
            // 从响应 body 读取业务码与消息字段
            Object actualCode = PlaceholderResolver.simpleJsonPath(body, "$." + check.getCodePath());
            Object messageObj = PlaceholderResolver.simpleJsonPath(body, "$." + check.getMessagePath());
            String message = messageObj != null ? String.valueOf(messageObj) : null;
            boolean passed = check.isSuccess(actualCode);

            // 写入步骤报告 http.bizCheck，供 Run 详情与失败分析展示
            Map<String, Object> bizCheck = new LinkedHashMap<>();
            bizCheck.put("codePath", check.getCodePath());
            bizCheck.put("actualCode", actualCode);
            bizCheck.put("successValues", check.successValuesForReport());
            bizCheck.put("passed", passed);
            if (message != null && !message.isBlank()) {
                bizCheck.put("message", message);
            }
            httpDetails.put("bizCheck", bizCheck);

            if (!passed) {
                // 业务码失败：步骤 failed，错误信息带上实际码与消息文案
                String errorMsg = "业务 code=" + actualCode
                        + (message != null && !message.isBlank() ? ": " + message : "");
                return StepResult.builder()
                        .nodeId(node.getId())
                        .nodeType(FlowNodeType.HTTP.getCode())
                        .nodeName(nodeName)
                        .edgeId(incomingEdgeId)
                        .status(StepResult.STATUS_FAILED)
                        .durationMs(durationMs)
                        .http(httpDetails)
                        .extracts(appliedExtracts)
                        .flowAfter(copyFlow(ctx))
                        .error(StepError.of(FlowErrorCode.TF_BIZ_CODE, errorMsg))
                        .build();
            }
        }

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.HTTP.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_PASSED)
                .durationMs(durationMs)
                .http(httpDetails)
                .extracts(appliedExtracts)
                .flowAfter(copyFlow(ctx))
                .build();
    }

    private static String resolveCallMode(Map<String, Object> data) {
        Object raw = data.get("callMode");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_CALLMODE, "HTTP 节点缺少 callMode");
        }
        String callMode = String.valueOf(raw).trim();
        if (!FlowHttpCallMode.isKnown(callMode)) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_CALLMODE, "HTTP 节点 callMode 无效：" + callMode);
        }
        return callMode;
    }

    private StepResult scriptFailed(GraphNode node, String incomingEdgeId, String nodeName, long t0,
                                      FlowRunContext ctx, ApiScriptExecutionResult scriptResult, String phase) {
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);
        FlowErrorCode code = scriptResult.getErrorCode() != null
                ? scriptResult.getErrorCode()
                : FlowErrorCode.TF_SCRIPT_ERROR;
        Map<String, Object> httpDetails = new LinkedHashMap<>();
        httpDetails.put(phase, scriptResult.toStepScriptDetails());
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.HTTP.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_FAILED)
                .durationMs(durationMs)
                .http(httpDetails)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(code, scriptResult.getErrorMessage() != null
                        ? scriptResult.getErrorMessage()
                        : "脚本执行失败"))
                .build();
    }

    private static StepResult failed(GraphNode node, String incomingEdgeId, String nodeName, long t0,
                                     FlowRunContext ctx, FlowErrorCode code, String message) {
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.HTTP.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_FAILED)
                .durationMs(durationMs)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(code, message))
                .build();
    }

    private static Long parseApiId(Object raw) {
        if (raw == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_UNBOUND, "未绑定 testProjectApiId");
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_UNBOUND, "testProjectApiId 无效");
        }
    }

    private static FlowErrorCode mapForwardError(String errorCode) {
        if ("TIMEOUT".equals(errorCode)) {
            return FlowErrorCode.TF_HTTP_TIMEOUT;
        }
        return FlowErrorCode.TF_STEP_ERROR;
    }

    private static Map<String, Object> toObjectHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Map.of();
        }
        return new HashMap<>(headers);
    }

    private static Object parseResponseBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return null;
        }
        String trimmed = bodyText.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JSON.parse(trimmed);
            } catch (Exception ignored) {
                return bodyText;
            }
        }
        return bodyText;
    }

    private static JSONArray toExtractsArray(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        if (raw instanceof List<?> list) {
            return JSON.parseArray(JSON.toJSONString(list));
        }
        return JSON.parseArray(JSON.toJSONString(raw));
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw);
        return s.isBlank() ? null : s;
    }
}
