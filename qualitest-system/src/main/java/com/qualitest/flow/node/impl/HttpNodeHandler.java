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
import com.qualitest.api.util.ExpectedResponseKindSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.context.AssetExtractPersistService;
import com.qualitest.flow.context.CompareRuleEvaluator;
import com.qualitest.flow.context.ExtractApplicator;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.http.HttpResponseBodyMediaSupport;
import com.qualitest.flow.http.HttpResponseBodyParser;
import com.qualitest.flow.http.HttpRetryUntilSupport;
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

import java.util.ArrayList;
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
 *       白名单可为整数或字符串（如 SUCCESS）；不在白名单内则步骤失败（TF_BIZ_CODE），
 *       并在 http.bizCheck 写入实际码、消息、解析失败时的 body 片段</li>
 * </ol>
 * 响应正文：JSON 转对象/数组；XML 按 Content-Type 或报文开头判定后转对象（去根节点）；
 * 有响应即调用 {@code putHttpResponse}：更新 lastResponse，并按本节点 id 写入 nodeResponses（含非 2xx）；
 * 快照含 bodyText、responseKind（json / xml / nonJson）、expectedMatch（不符期望不直接导致本步失败）。
 * XML 时步骤报告 response 另带 bodyText。
 * extracts：默认仅步骤最终通过才执行并落盘 asset；
 * extractsOnFailure=write 时失败也会做内存抽取，但 asset 落盘仍只在步骤通过时发生。
 * 业务码路径规范化为绝对路径后再读，避免 codePath 已带 {@code $.} 时再拼一层变成无效路径。
 * <p>
 * 可选 {@code data.retryUntil}（重试直到通过）：
 * <ul>
 *   <li>{@code maxAttempts}：本节点最多发出几次请求，缺省 1</li>
 *   <li>{@code intervalMs}：两次请求之间的等待毫秒</li>
 *   <li>{@code until}：可选通过条件（left/operator/right，全部满足才算业务状态到位）；
 *       不配则只看状态码与业务码</li>
 * </ul>
 * 状态码失败、业务码失败、通过条件未满足或请求超时，且仍有剩余次数时，等待间隔后再请求；
 * 未绑定接口、callMode 非法、外联被拒、脚本错误等立即失败且不再请求。
 * 整节点无论请求几轮，在运行时间线里只记为一步；多轮时步骤详情写入 {@code http.retry}。
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

    /**
     * 执行 HTTP 节点：先解析 retryUntil，再按次数上限循环请求，组装最终 StepResult。
     */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        String nodeName = resolveNodeName(node);
        HttpRetryUntilSupport.Resolved retry = HttpRetryUntilSupport.resolve(data);

        try {
            return executeAttempts(ctx, node, incomingEdgeId, nodeName, data, t0, retry);
        } catch (FlowExecutionException e) {
            return failed(node, incomingEdgeId, nodeName, System.currentTimeMillis() - t0, ctx,
                    e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            return failed(node, incomingEdgeId, nodeName, System.currentTimeMillis() - t0, ctx,
                    FlowErrorCode.TF_STEP_ERROR, e.getMessage() != null ? e.getMessage() : "HTTP 步骤异常");
        }
    }

    /**
     * 按 {@code maxAttempts} 循环发请求。
     * 次数为 1 时当场写 extracts；次数大于 1 时中间轮不写 extracts，
     * 在成功、不可再试或次数用尽时再一次性补写抽取结果。
     */
    private StepResult executeAttempts(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data, long t0, HttpRetryUntilSupport.Resolved retry) {
        List<Map<String, Object>> attemptSummaries = new ArrayList<>();
        int max = Math.max(1, retry.maxAttempts());
        for (int attempt = 1; attempt <= max; attempt++) {
            if (attempt > 1) {
                StepResult interrupted = sleepBetweenAttempts(
                        retry.intervalMs(), node, incomingEdgeId, nodeName, t0, ctx);
                if (interrupted != null) {
                    return attachRetryReportIfNeeded(
                            interrupted, retry, attempt - 1, attemptSummaries, t0);
                }
            }
            // 只打一轮时当场抽取；多轮时延后到终止再 commitDeferredExtracts，避免中间响应污染变量
            boolean allowExtractsNow = max == 1;
            StepResult last = executeOnce(ctx, node, incomingEdgeId, nodeName, data, t0, allowExtractsNow);
            attemptSummaries.add(HttpRetryUntilSupport.attemptSummary(attempt, last));

            boolean passed = RunStatus.PASSED.getCode().equals(last.getStatus());
            boolean retryable = HttpRetryUntilSupport.isRetryable(last);
            boolean terminal = passed || !retryable || attempt >= max;
            if (!terminal) {
                continue;
            }
            if (!allowExtractsNow) {
                last = commitDeferredExtracts(last, data, ctx, node);
            }
            return attachRetryReportIfNeeded(last, retry, attempt, attemptSummaries, t0);
        }
        throw new IllegalStateException("HTTP retry loop exited without result");
    }

    /**
     * 按 callMode 执行一轮 HTTP：project 绑项目接口，external 走外联 URL。
     *
     * @param allowExtracts false 时跳过变量抽取与素材库落盘（多轮中间请求用）
     */
    private StepResult executeOnce(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data, long t0, boolean allowExtracts) {
        String callMode = resolveCallMode(data);
        if (FlowHttpCallMode.isExternal(callMode)) {
            return executeExternal(ctx, node, incomingEdgeId, nodeName, data, t0, allowExtracts);
        }
        return executeProject(ctx, node, incomingEdgeId, nodeName, data, t0, allowExtracts);
    }

    /**
     * 两次请求之间的阻塞等待。
     *
     * @return 被中断时返回失败 StepResult；正常睡完或间隔为 0 时返回 null
     */
    private static StepResult sleepBetweenAttempts(
            long intervalMs, GraphNode node, String incomingEdgeId, String nodeName,
            long t0, FlowRunContext ctx) {
        if (intervalMs <= 0) {
            return null;
        }
        try {
            Thread.sleep(intervalMs);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(node, incomingEdgeId, nodeName, System.currentTimeMillis() - t0, ctx,
                    FlowErrorCode.TF_STEP_ERROR, "HTTP 重试等待被中断");
        }
    }

    /**
     * 多轮请求结束时补写 extracts：成功则内存抽取并落盘素材；
     * 失败且 extractsOnFailure=write 时只写内存不落盘。
     */
    private StepResult commitDeferredExtracts(
            StepResult result, Map<String, Object> data, FlowRunContext ctx, GraphNode node) {
        if (result == null) {
            return null;
        }
        boolean passed = RunStatus.PASSED.getCode().equals(result.getStatus());
        if (!shouldApplyExtracts(data, passed)) {
            return result;
        }
        FlowRunContext.HttpResponseSnapshot snapshot = ctx.getNodeResponse(node.getId());
        if (snapshot == null) {
            return result;
        }
        JSONArray extractsConfig = toExtractsArray(data.get("extracts"));
        List<JSONObject> applied = ExtractApplicator.apply(extractsConfig, ctx, snapshot);
        if (passed) {
            assetExtractPersistService.persistFromExtractConfig(ctx, extractsConfig, applied);
        }
        return result.toBuilder()
                .extracts(applied)
                .flowAfter(copyFlow(ctx))
                .durationMs(result.getDurationMs())
                .build();
    }

    /**
     * 需要展示重试信息时，把 {@code http.retry} 写入步骤详情，并把 durationMs 刷新为整节点总耗时
     * （含各轮请求与间隔等待）。仅打一轮且未配通过条件时不写 retry 字段。
     */
    private static StepResult attachRetryReportIfNeeded(
            StepResult result, HttpRetryUntilSupport.Resolved retry,
            int attemptsDone, List<Map<String, Object>> attemptSummaries, long t0) {
        if (result == null) {
            return null;
        }
        long totalMs = Math.max(0, System.currentTimeMillis() - t0);
        boolean reportRetry = retry.needsLoop() || retry.hasUntil()
                || (attemptSummaries != null && attemptSummaries.size() > 1);
        if (!reportRetry) {
            return result.getDurationMs() == totalMs
                    ? result
                    : result.toBuilder().durationMs(totalMs).build();
        }
        Map<String, Object> http = result.getHttp() != null
                ? new LinkedHashMap<>(result.getHttp())
                : new LinkedHashMap<>();
        http.put("retry", HttpRetryUntilSupport.buildRetryReport(retry, attemptsDone, attemptSummaries));
        http.put("durationMs", totalMs);
        return result.toBuilder().http(http).durationMs(totalMs).build();
    }

    private StepResult executeProject(FlowRunContext ctx, GraphNode node, String incomingEdgeId,
                                      String nodeName, Map<String, Object> data, long t0,
                                      boolean allowExtracts) {
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
        HttpResponseBodyParser.Parsed parsedBody = HttpResponseBodyParser.parse(
                forwardResult.getBodyText(), forwardResult.getResponseHeaders());

        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(parsedBody.body())
                .bodyText(forwardResult.getBodyText())
                .durationMs(durationMs)
                .build();
        ctx.putHttpResponse(node.getId(), snapshot);

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
                status, responseHeaders, parsedBody, forwardResult, preScriptResult, postScriptResult,
                effectiveApi, allowExtracts);
    }

    private StepResult executeExternal(FlowRunContext ctx, GraphNode node, String incomingEdgeId,
                                       String nodeName, Map<String, Object> data, long t0,
                                       boolean allowExtracts) {
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
        HttpResponseBodyParser.Parsed parsedBody = HttpResponseBodyParser.parse(
                forwardResult.getBodyText(), forwardResult.getResponseHeaders());

        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(parsedBody.body())
                .bodyText(forwardResult.getBodyText())
                .durationMs(durationMs)
                .build();
        ctx.putHttpResponse(node.getId(), snapshot);

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
                status, responseHeaders, parsedBody, forwardResult, preScriptResult, postScriptResult,
                null, allowExtracts);
    }

    /**
     * 组装一轮 HTTP 的步骤结果：写入运行时响应快照，填充步骤报告字段，
     * 依次做状态码门禁、业务码校验、可选 until 通过条件，再按需抽取变量。
     *
     * @param parsedBody    正文解析结果（形态与对象）
     * @param api           绑定的项目接口；外联时为 null
     * @param allowExtracts false 时跳过抽取与素材落盘（多轮中间请求）
     */
    private StepResult buildHttpStepResult(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data,
            FlowHttpRequestBuilder.BuiltHttpRequest built, long durationMs,
            int status, Map<String, Object> responseHeaders, HttpResponseBodyParser.Parsed parsedBody,
            DebugHttpForwardResult forwardResult,
            ApiScriptExecutionResult preScriptResult,
            ApiScriptExecutionResult postScriptResult,
            TestProjectApi api,
            boolean allowExtracts) {
        Object body = parsedBody.body();
        // 形态取正文解析结果；对照接口期望形态写入 expectedMatch（不符不导致本步失败）
        String responseKind = parsedBody.kind();
        boolean expectedMatch = api == null
                || ExpectedResponseKindSupport.matchesOrSkipByKind(api.getResponseConfig(), responseKind);

        FlowRunContext.HttpResponseSnapshot snapshot = FlowRunContext.HttpResponseSnapshot.builder()
                .status(status)
                .headers(responseHeaders)
                .body(body)
                .bodyText(forwardResult.getBodyText())
                .durationMs(durationMs)
                .responseKind(responseKind)
                .expectedMatch(expectedMatch)
                .build();
        ctx.putHttpResponse(node.getId(), snapshot);

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
        // XML 已转对象时在报告中保留原文
        if (parsedBody.isXml()) {
            responseSnapshot.put("bodyText", forwardResult.getBodyText());
        }
        // 报告中的实际形态与期望是否匹配
        responseSnapshot.put("responseKind", responseKind);
        responseSnapshot.put("expectedMatch", expectedMatch);
        // 裸媒体：只写 bodyMedia 元数据（stored=false），不落 bodyBase64
        Map<String, Object> bodyMedia = HttpResponseBodyMediaSupport.buildMarker(forwardResult);
        if (bodyMedia != null) {
            responseSnapshot.put("bodyMedia", bodyMedia);
        }
        httpDetails.put("response", responseSnapshot);
        httpDetails.put("durationMs", durationMs);
        httpDetails.put("responseKind", responseKind);
        httpDetails.put("expectedMatch", expectedMatch);
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
            List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, false, allowExtracts);
            return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                    RunStatus.FAILED.getCode(),
                    StepError.of(FlowErrorCode.TF_HTTP_STATUS, "HTTP " + status));
        }

        HttpRetryUntilSupport.Resolved retryCfg = HttpRetryUntilSupport.resolve(data);

        // statusCheck 已放行但非 2xx（如 whitelist 含 401）：不做业务码；仍要过 until
        if (status < 200 || status >= 300) {
            StepResult untilFailed = evaluateUntilOrNull(
                    ctx, node, incomingEdgeId, nodeName, data, durationMs, httpDetails,
                    snapshot, allowExtracts, retryCfg);
            if (untilFailed != null) {
                return untilFailed;
            }
            return passedHttpStep(node, incomingEdgeId, nodeName, data, durationMs,
                    httpDetails, ctx, snapshot, allowExtracts);
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
                List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, false, allowExtracts);
                String errorMsg = formatBizCodeFailure(actualCode, message, check.getCodePath(), body);
                return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                        RunStatus.FAILED.getCode(),
                        StepError.of(FlowErrorCode.TF_BIZ_CODE, errorMsg));
            }
        }

        StepResult untilFailed = evaluateUntilOrNull(
                ctx, node, incomingEdgeId, nodeName, data, durationMs, httpDetails,
                snapshot, allowExtracts, retryCfg);
        if (untilFailed != null) {
            return untilFailed;
        }

        return passedHttpStep(node, incomingEdgeId, nodeName, data, durationMs,
                httpDetails, ctx, snapshot, allowExtracts);
    }

    /**
     * 状态码与业务码（若适用）以及通过条件均已满足后的成功收尾：
     * 按 allowExtracts 决定是否抽取变量并回写素材库，返回 passed 步骤。
     */
    private StepResult passedHttpStep(
            GraphNode node, String incomingEdgeId, String nodeName, Map<String, Object> data,
            long durationMs, Map<String, Object> httpDetails, FlowRunContext ctx,
            FlowRunContext.HttpResponseSnapshot snapshot, boolean allowExtracts) {
        List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, true, allowExtracts);
        if (allowExtracts) {
            JSONArray extractsConfig = toExtractsArray(data.get("extracts"));
            assetExtractPersistService.persistFromExtractConfig(ctx, extractsConfig, appliedExtracts);
        }
        return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                RunStatus.PASSED.getCode(), null);
    }

    /**
     * 求值 {@code retryUntil.until} 通过条件。
     * 未配置任何条件时视为满足，返回 null；
     * 有条件且全部通过时返回 null；
     * 任一条未过时返回 failed 步骤（错误码 TF_ASSERT_FAILED），并在 http.until 写入各条实测。
     */
    private StepResult evaluateUntilOrNull(
            FlowRunContext ctx, GraphNode node, String incomingEdgeId, String nodeName,
            Map<String, Object> data, long durationMs, Map<String, Object> httpDetails,
            FlowRunContext.HttpResponseSnapshot snapshot, boolean allowExtracts,
            HttpRetryUntilSupport.Resolved retry) {
        if (retry == null || !retry.hasUntil()) {
            return null;
        }
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        boolean allPassed = true;
        for (JSONObject rule : retry.untilRules()) {
            CompareRuleEvaluator.EvalDetail detail = CompareRuleEvaluator.evalDetailed(rule, ctx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("left", rule.getString("left"));
            row.put("operator", rule.getOrDefault("operator", "eq"));
            row.put("right", rule.getString("right"));
            row.put("passed", detail.passed());
            row.put("leftActual", AssertNodeHandler.serializeLeftActual(detail.leftActual()));
            ruleResults.add(row);
            if (!detail.passed()) {
                allPassed = false;
            }
        }
        Map<String, Object> untilReport = new LinkedHashMap<>();
        untilReport.put("rules", ruleResults);
        untilReport.put("passed", allPassed);
        httpDetails.put("until", untilReport);
        if (allPassed) {
            return null;
        }
        List<JSONObject> appliedExtracts = maybeApplyExtracts(data, ctx, snapshot, false, allowExtracts);
        return httpStepResult(node, incomingEdgeId, nodeName, durationMs, httpDetails, appliedExtracts, ctx,
                RunStatus.FAILED.getCode(),
                StepError.of(FlowErrorCode.TF_ASSERT_FAILED, "通过条件未满足"));
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
     * allowExtracts=false（多轮中间请求）时一律跳过。
     * 此处只写内存 flow/asset，不负责素材库落盘。
     */
    private List<JSONObject> maybeApplyExtracts(
            Map<String, Object> data, FlowRunContext ctx,
            FlowRunContext.HttpResponseSnapshot snapshot, boolean stepPassed, boolean allowExtracts) {
        if (!allowExtracts || !shouldApplyExtracts(data, stepPassed)) {
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
        // 附加宿主能力提示：改 body 须整对象重赋；编码用 api.base64Encode（无浏览器 btoa）；签名加密用宿主工具函数
        msg = msg + "；可用 api.request.url/method/headers.add、api.request.body=整对象、api.base64Encode、api.jsonParse/jsonStringify、"
                + "签名加密 api.md5/sha256/hmacSha256/rsaSign/aesEncrypt 等（无 btoa/pm/CryptoJS；body 嵌套赋值不回写）";
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
