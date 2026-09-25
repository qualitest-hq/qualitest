package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.script.ApiRequestScriptService;
import com.qualitest.api.script.ApiScriptContext;
import com.qualitest.api.script.ApiScriptExecutionResult;
import com.qualitest.api.script.ApiScriptSupport;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.context.AssetExtractPersistService;
import com.qualitest.flow.context.ExtractApplicator;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.http.HttpResponseBodyMediaSupport;
import com.qualitest.flow.http.HttpStepDetailsDesensitizer;
import com.qualitest.flow.http.StatusCheckResolver;
import com.qualitest.flow.http.SuccessCheckResolver;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
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
 * Cookie 鉴权与 Bearer 相同：登录 extracts（含 from=setCookie）写入 flow.*，
 * 后续由项目 Profile 托管头带上；节点不再使用 useRunSession。
 * <p>
 * 成功判定顺序（statusCheck 与 successCheck 分开做，不合并成一步）：
 * <ol>
 *   <li>HTTP 状态码：默认 statusCheck.mode=2xx，非 2xx 记 TF_HTTP_STATUS；
 *       whitelist 时仅 values 内状态码通过；off 时任意状态码通过（连接失败仍失败）</li>
 *   <li>仅 HTTP 2xx 时再做业务码：successCheck.mode 非 off 则读 body 业务码；
 *       不在成功白名单内则步骤失败（TF_BIZ_CODE），并在 http.bizCheck 写入实际码、消息、解析失败时的 body 片段</li>
 * </ol>
 * 有响应即写入 lastResponse（含非 2xx）。
 * extracts：默认仅步骤最终通过才执行并落盘 asset；
 * extractsOnFailure=write 时失败也会做内存抽取，但 asset 落盘仍只在步骤通过时发生。
 * 业务码路径经 toAbsolutePath 规范化后再读，避免 codePath 已带 {@code $.} 时再拼一层变成无效路径。
 */
@Component
public class HttpNodeHandler extends AbstractStubNodeHandler {

    private final ITestProjectApiService testProjectApiService;
    private final IDebugHttpForwardService debugHttpForwardService;
    private final ApiRequestScriptService apiRequestScriptService;
    private final AssetExtractPersistService assetExtractPersistService;

    public HttpNodeHandler(ITestProjectApiService testProjectApiService,
                           IDebugHttpForwardService debugHttpForwardService,
                           ApiRequestScriptService apiRequestScriptService,
                           AssetExtractPersistService assetExtractPersistService) {
        super(FlowNodeType.HTTP);
        this.testProjectApiService = testProjectApiService;
        this.debugHttpForwardService = debugHttpForwardService;
        this.apiRequestScriptService = apiRequestScriptService;
        this.assetExtractPersistService = assetExtractPersistService;
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
        // 叠测值与响应 example；发出 body 由 issuedRequestConfig 再按 测值→节点 叠层
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
                status, responseHeaders, body, forwardResult, preScriptResult, postScriptResult, effectiveApi);
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
                status, responseHeaders, body, forwardResult, preScriptResult, postScriptResult, null);
    }

    private StepResult buildHttpStepResult(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data,
            FlowHttpRequestBuilder.BuiltHttpRequest built, long durationMs,
            int status, Map<String, Object> responseHeaders, Object body,
            DebugHttpForwardResult forwardResult,
            ApiScriptExecutionResult preScriptResult,
            ApiScriptExecutionResult postScriptResult,
            TestProjectApi api) {
        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(body)
                .durationMs(durationMs)
                .build();

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
        // 裸媒体：只写 bodyMedia 元数据（stored=false），不落 bodyBase64
        Map<String, Object> bodyMedia = HttpResponseBodyMediaSupport.buildMarker(forwardResult);
        if (bodyMedia != null) {
            responseSnapshot.put("bodyMedia", bodyMedia);
        }
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

        // HTTP 状态门禁：默认要求 2xx；whitelist/off 可放行非 2xx，供后续 Condition 分支
        StatusCheckResolver.Resolved statusCheck = StatusCheckResolver.resolve(data);
        Map<String, Object> statusCheckReport = new LinkedHashMap<>();
        statusCheckReport.put("mode", statusCheck.getMode());
        if (!statusCheck.getValues().isEmpty()) {
            statusCheckReport.put("values", statusCheck.getValues());
        }
        boolean statusPassed = statusCheck.passes(status);
        statusCheckReport.put("passed", statusPassed);
        httpDetails.put("statusCheck", statusCheckReport);

        // 状态门禁失败：步骤失败；默认不写 extracts，避免把失败响应里的脏值写入 flow/asset
        if (!statusPassed) {
            List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, false);
            return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                    RunStatus.FAILED.getCode(),
                    StepError.of(FlowErrorCode.TF_HTTP_STATUS, "HTTP " + status));
        }

        // statusCheck 已放行但非 2xx（如 whitelist 含 401）：步骤通过，不做业务码校验
        if (status < 200 || status >= 300) {
            List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, true);
            return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                    RunStatus.PASSED.getCode(), null);
        }

        // HTTP 2xx：按节点 successCheck / 项目响应约定校验 body 业务码
        String apiPath = api != null ? api.getApiPath() : null;
        if (apiPath == null || apiPath.isBlank()) {
            apiPath = extractPathFromUrl(built.getUrl());
        }
        String conventionJson = ProjectAuthConfigSupport.resolveResponseConventionJson(
                apiPath, ctx.getProjectAuthConfig());
        SuccessCheckResolver.Resolved check = SuccessCheckResolver.resolve(
                data, built.getCallMode(), api, conventionJson);
        if (check.shouldApply()) {
            // codePath/messagePath 统一成绝对 JsonPath 再读，防止已带 $. 时重复拼接
            String codeAbs = JsonPathFacade.toAbsolutePath(check.getCodePath());
            String msgAbs = JsonPathFacade.toAbsolutePath(check.getMessagePath());
            Object actualCode = PlaceholderResolver.simpleJsonPath(body, codeAbs);
            Object messageObj = PlaceholderResolver.simpleJsonPath(body, msgAbs);
            String message = messageObj != null ? String.valueOf(messageObj) : null;
            boolean passed = check.isSuccess(actualCode);

            Map<String, Object> bizCheck = new LinkedHashMap<>();
            bizCheck.put("codePath", check.getCodePath());
            bizCheck.put("actualCode", actualCode);
            bizCheck.put("successValues", check.successValuesForReport());
            bizCheck.put("passed", passed);
            if (message != null && !message.isBlank()) {
                bizCheck.put("message", message);
            }
            // 读不到业务码时记下片段，方便报告里对照真实响应
            if (actualCode == null) {
                bizCheck.put("parseMissed", true);
                bizCheck.put("bodySnippet", bodySnippet(body, 200));
            }
            httpDetails.put("bizCheck", bizCheck);

            if (!passed) {
                List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, false);
                String errorMsg = formatBizCodeFailure(actualCode, message, check.getCodePath(), body);
                return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                        RunStatus.FAILED.getCode(),
                        StepError.of(FlowErrorCode.TF_BIZ_CODE, errorMsg));
            }
        }

        // 步骤最终通过：执行 extracts，并把配置为 asset 的抽取结果落盘
        JSONArray extractsConfig = toExtractsArray(data.get("extracts"));
        List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, true);
        assetExtractPersistService.persistFromExtractConfig(ctx, extractsConfig, appliedExtracts);

        return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                RunStatus.PASSED.getCode(), null);
    }

    /**
     * 组装本步 HTTP 的 StepResult。
     *
     * @param status 步骤状态码（passed / failed）
     * @param error  失败时的错误；通过时传 null
     */
    private static StepResult httpStepResult(
            GraphNode node, String incomingEdgeId, String nodeName, long durationMs,
            Map<String, Object> httpDetails, List<JSONObject> extracts, FlowRunContext ctx,
            String status, StepError error) {
        var builder = StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.HTTP.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(status)
                .durationMs(durationMs)
                .http(httpDetails)
                .extracts(extracts)
                .flowAfter(copyFlow(ctx));
        if (error != null) {
            builder.error(error);
        }
        return builder.build();
    }

    /**
     * 本步是否执行 extracts。
     * 步骤通过：一律执行。
     * 步骤失败：仅当节点 extractsOnFailure=write 时执行（默认 skip，防止失败响应污染变量）。
     */
    private static boolean shouldApplyExtracts(Map<String, Object> data, boolean stepPassed) {
        if (stepPassed) {
            return true;
        }
        return isExtractsOnFailureWrite(data);
    }

    /** 节点是否显式要求失败时仍写 extracts（data.extractsOnFailure=write）。 */
    private static boolean isExtractsOnFailureWrite(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object raw = data.get("extractsOnFailure");
        return raw != null && "write".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    /**
     * 按步骤成败与 extractsOnFailure 决定是否抽取；不抽则返回空列表。
     * 此处只写内存 flow/asset，不负责 asset 落盘。
     */
    private List<JSONObject> maybeApplyExtracts(
            Map<String, Object> data, FlowRunContext ctx,
            FlowRunContext.HttpResponseSnapshot snapshot, boolean stepPassed) {
        if (!shouldApplyExtracts(data, stepPassed)) {
            return List.of();
        }
        JSONArray extractsConfig = toExtractsArray(data.get("extracts"));
        return ExtractApplicator.apply(extractsConfig, ctx, snapshot);
    }

    /**
     * 业务码失败时的错误文案。
     * 读到码：输出 {@code 业务 code=实际值}，有 message 则追加。
     * 读不到码：输出 codePath 与 body 截断片段，便于对照响应结构。
     */
    static String formatBizCodeFailure(Object actualCode, String message, String codePath, Object body) {
        if (actualCode != null) {
            return "业务 code=" + actualCode
                    + (message != null && !message.isBlank() ? ": " + message : "");
        }
        return "业务 code=null (codePath=" + (codePath != null ? codePath : "")
                + ", body=" + bodySnippet(body, 200) + ")";
    }

    /** 把响应 body 截成最多 maxLen 字符，超长加省略号；null 返回空串。 */
    static String bodySnippet(Object body, int maxLen) {
        if (body == null) {
            return "";
        }
        String s = body instanceof String str ? str : JSON.toJSONString(body);
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen)) + "...";
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
        String msg = scriptResult.getErrorMessage() != null
                ? scriptResult.getErrorMessage()
                : "脚本执行失败";
        // 附加宿主能力提示：改 body 须整对象重赋；编码用 api.base64Encode（无浏览器 btoa）
        msg = msg + "；可用 api.request.url/method/headers.add、api.request.body=整对象、api.base64Encode、api.jsonParse/jsonStringify（无 btoa/pm；body 嵌套赋值不回写）";
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.HTTP.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.FAILED.getCode())
                .durationMs(durationMs)
                .http(httpDetails)
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(code, msg))
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
                .status(RunStatus.FAILED.getCode())
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

    /** 从完整 URL 抽出 path，供无绑定 API 时按路径选端并取响应约定。 */
    private static String extractPathFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "/";
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String path = uri.getPath();
            return path != null && !path.isBlank() ? path : "/";
        } catch (Exception e) {
            int scheme = url.indexOf("://");
            int start = scheme >= 0 ? url.indexOf('/', scheme + 3) : url.indexOf('/');
            if (start < 0) {
                return "/";
            }
            int q = url.indexOf('?', start);
            return q >= 0 ? url.substring(start, q) : url.substring(start);
        }
    }
}
