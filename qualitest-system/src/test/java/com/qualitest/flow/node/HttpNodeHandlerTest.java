package com.qualitest.flow.node;

import com.qualitest.api.util.ApiConfigV2TestFixtures;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.script.ApiRequestScriptService;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.HttpNodeHandler;
import com.qualitest.flow.script.ScriptRuntime;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.service.ITestProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HttpNodeHandler} 单元测试：验证测试流程中 HTTP 节点的完整执行链路。
 * <p>
 * 被测对象负责：根据节点绑定的 {@code testProjectApiId} 加载项目 API 定义 → 解析占位符与前置脚本
 * → 调用 {@link IDebugHttpForwardService} 发起真实 HTTP 转发 → 校验 2xx 状态码 → 按 extracts 配置
 * 从响应中提取变量写入 {@code flow} 作用域 → 组装 {@link StepResult}（含 http 详情）。
 * <p>
 * 依赖全部 Mock：{@link ITestProjectApiService} 返回 API 元数据，
 * {@link IDebugHttpForwardService} 模拟 HTTP 响应，不发起真实网络请求。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=HttpNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpNodeHandlerTest {

    private ITestProjectApiService apiService;
    private IDebugHttpForwardService forwardService;
    private HttpNodeHandler handler;
    private FlowRunContext ctx;

    @BeforeEach
    void setUp() {
        apiService = mock(ITestProjectApiService.class);
        forwardService = mock(IDebugHttpForwardService.class);
        ApiRequestScriptService scriptService = new ApiRequestScriptService(new ScriptRuntime(forwardService), forwardService);
        handler = new HttpNodeHandler(apiService, forwardService, scriptService);

        Map<String, Object> env = new HashMap<>();
        env.put("baseUrl", "http://localhost:8080");
        Map<String, Object> flow = new HashMap<>();
        flow.put("loginUser", "admin");
        ctx = FlowRunContext.builder().env(env).flow(flow).build();
    }

    /**
     * 正常路径：Mock 返回 200 + JSON body，节点配置 JsonPath extracts。
     * 期望：步骤状态 passed；{@code flow.token}、{@code flow.code} 从响应体提取成功；
     * {@code result.http} 非空（含请求/响应摘要）。
     */
    @Test
    @Order(1)
    void execute_success_extractsFlow() {
        begin("execute_success_extractsFlow");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_JSON_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "application/json"),
                        "{\"code\":200,\"data\":{\"token\":\"tok-1\",\"code\":0}}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("extracts", List.of(
                Map.of("from", "body", "expr", "$.data.token", "scope", "flow", "name", "token"),
                Map.of("from", "body", "expr", "$.data.code", "scope", "flow", "name", "code")
        ));
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("tok-1", ctx.getFlow().get("token"));
        assertEquals(0L, ((Number) ctx.getFlow().get("code")).longValue());
        assertNotNull(result.getHttp());

        log("status=" + result.getStatus() + " flow.token=" + ctx.getFlow().get("token")
                + " flow.code=" + ctx.getFlow().get("code"));
        end("execute_success_extractsFlow");
    }

    /**
     * HTTP 状态码非 2xx 时步骤应立即失败，不再执行 extracts。
     * 期望：状态 failed，错误码 {@link FlowErrorCode#TF_HTTP_STATUS}。
     */
    @Test
    @Order(2)
    void execute_non2xx_fails() {
        begin("execute_non2xx_fails");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(401, "Unauthorized", Map.of(), "{\"error\":\"auth\"}")
        );

        GraphNode node = GraphNode.builder()
                .id("n1")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_STATUS.getCode(), result.getError().getCode());
        log("status=" + result.getStatus() + " error=" + result.getError().getCode());
        end("execute_non2xx_fails");
    }

    /**
     * 节点 data 中缺少 {@code testProjectApiId}（未绑定项目 API）时无法执行。
     * 期望：状态 failed，错误码 {@link FlowErrorCode#TF_HTTP_UNBOUND}。
     */
    @Test
    @Order(3)
    void execute_unboundApiId_fails() {
        begin("execute_unboundApiId_fails");
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();
        StepResult result = handler.execute(ctx, node, null);
        assertEquals(FlowErrorCode.TF_HTTP_UNBOUND.getCode(), result.getError().getCode());
        log("error=" + result.getError().getCode() + " message=" + result.getError().getMessage());
        end("execute_unboundApiId_fails");
    }

    /**
     * API 定义上的前置脚本（{@code preRequestScript}）应在转发前执行，可改写请求参数。
     * 本用例脚本调用 {@code api.request.headers.add} 添加自定义头 X-Flow。
     * 期望：转发参数中含该请求头；步骤 passed；{@code result.http.preScript} 记录脚本执行摘要。
     */
    @Test
    @Order(4)
    void execute_preScriptAddsHeader() {
        begin("execute_preScriptAddsHeader");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .preRequestScript("api.request.headers.add({key:'X-Flow', value:'yes'});")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenAnswer(invocation -> {
            DebugHttpForwardParams params = invocation.getArgument(0);
            boolean hasHeader = params.getHeaders() != null && params.getHeaders().stream()
                    .anyMatch(h -> "X-Flow".equals(h.getName()) && "yes".equals(h.getValue()));
            assertTrue(hasHeader);
            return DebugHttpForwardResult.success(200, "OK", Map.of(), "{\"code\":200}");
        });

        GraphNode node = GraphNode.builder()
                .id("n1")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNotNull(result.getHttp().get("preScript"));
        end("execute_preScriptAddsHeader");
    }

    /**
     * 外联 HTTP 节点：不绑定项目 API，直接请求 externalUrl。
     * 期望：passed；extracts 写入 flow；result.http.callMode=external。
     */
    @Test
    @Order(5)
    void execute_external_success() {
        begin("execute_external_success");
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "application/json"),
                        "{\"access_token\":\"tok-ext\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        data.put("requestBody", "grant_type=client_credentials");
        data.put("extracts", List.of(
                Map.of("from", "body", "expr", "$.access_token", "scope", "flow", "name", "token")
        ));
        GraphNode node = GraphNode.builder().id("n-ext").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("tok-ext", ctx.getFlow().get("token"));
        assertEquals("external", result.getHttp().get("callMode"));
        log("flow.token=" + ctx.getFlow().get("token") + " callMode=external");
        end("execute_external_success");
    }

    /**
     * 运行上下文未授权外联 HTTP 时应拒绝执行。
     * 期望：failed；错误码 {@link FlowErrorCode#TF_HTTP_EXTERNAL_DENIED}。
     */
    @Test
    @Order(6)
    void execute_external_deniedWhenNoPermission() {
        begin("execute_external_deniedWhenNoPermission");
        ctx.setExternalHttpPermitted(false);

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder().id("n-denied").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED.getCode(), result.getError().getCode());
        log("error=" + result.getError().getCode());
        end("execute_external_deniedWhenNoPermission");
    }

    /**
     * 外联 HTTP 节点 preScript 应在转发前执行并可添加请求头。
     * 期望：passed；转发参数含 X-Ext；result.http.preScript 非空。
     */
    @Test
    @Order(7)
    void execute_external_preScriptAddsHeader() {
        begin("execute_external_preScriptAddsHeader");
        when(forwardService.forward(any())).thenAnswer(invocation -> {
            DebugHttpForwardParams params = invocation.getArgument(0);
            boolean hasHeader = params.getHeaders() != null && params.getHeaders().stream()
                    .anyMatch(h -> "X-Ext".equals(h.getName()) && "1".equals(h.getValue()));
            assertTrue(hasHeader);
            return DebugHttpForwardResult.success(200, "OK", Map.of(), "{}");
        });

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        data.put("preScript", "api.request.headers.add({key:'X-Ext', value:'1'});");
        GraphNode node = GraphNode.builder().id("n-pre").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNotNull(result.getHttp().get("preScript"));
        log("preScript recorded=true");
        end("execute_external_preScriptAddsHeader");
    }

    /**
     * useRunSession=true 时 HTTP 响应 Set-Cookie 应写入 RunSession。
     * 期望：passed；runSession 非空；result.http 含 runSessionCookies。
     */
    @Test
    @Order(8)
    void execute_useRunSession_absorbsCookies() {
        begin("execute_useRunSession_absorbsCookies");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK",
                        Map.of("Set-Cookie", "sid=abc; Path=/"), "{\"code\":200}")
        );

        GraphNode node = GraphNode.builder()
                .id("n-cookie")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001", "useRunSession", true))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNotNull(result.getHttp().get("runSessionCookies"));
        assertFalse(ctx.getRunSession().isEmpty());
        log("runSessionEmpty=false cookies=" + result.getHttp().get("runSessionCookies"));
        end("execute_useRunSession_absorbsCookies");
    }

    /**
     * HTTP 200 但业务 code 非成功值时步骤应失败。
     * 期望：failed；{@link FlowErrorCode#TF_BIZ_CODE}；http.bizCheck.passed=false。
     */
    @Test
    @Order(9)
    void execute_bizCodeFail_failsStep() {
        begin("execute_bizCodeFail_failsStep");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(),
                        "{\"code\":500,\"msg\":\"手机号或密码错误\"}")
        );

        GraphNode node = GraphNode.builder()
                .id("n-biz")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("手机号或密码错误"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bizCheck = (Map<String, Object>) result.getHttp().get("bizCheck");
        assertNotNull(bizCheck);
        assertEquals(false, bizCheck.get("passed"));
        assertEquals(500, ((Number) bizCheck.get("actualCode")).intValue());
        log("error=" + result.getError().getCode() + " msg=" + result.getError().getMessage());
        end("execute_bizCodeFail_failsStep");
    }

    /**
     * 节点 successCheck.mode=off 时跳过业务码校验。
     */
    @Test
    @Order(10)
    void execute_successCheckOff_skipsBizCode() {
        begin("execute_successCheckOff_skipsBizCode");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(),
                        "{\"code\":500,\"msg\":\"ignored\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-off").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNull(result.getHttp().get("bizCheck"));
        log("status=" + result.getStatus());
        end("execute_successCheckOff_skipsBizCode");
    }

    /**
     * external 默认不校验业务码（即使 body 含失败 code）。
     */
    @Test
    @Order(11)
    void execute_external_skipsBizCodeByDefault() {
        begin("execute_external_skipsBizCodeByDefault");
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(),
                        "{\"code\":500,\"msg\":\"fail\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder().id("n-ext-biz").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNull(result.getHttp().get("bizCheck"));
        log("status=" + result.getStatus());
        end("execute_external_skipsBizCodeByDefault");
    }

    /**
     * API biz_code_config.successValues=[0] 时 code=0 通过、code=200 失败。
     */
    @Test
    @Order(12)
    void execute_apiBizCodeConfig_overridesDefault() {
        begin("execute_apiBizCodeConfig_overridesDefault");
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigV2TestFixtures.REQUEST_NONE_BODY)
                .bizCodeConfig("{\"successValues\":[0]}")
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);

        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(), "{\"code\":0,\"msg\":\"ok\"}")
        );
        GraphNode nodeOk = GraphNode.builder()
                .id("n-api-ok")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();
        StepResult ok = handler.execute(ctx, nodeOk, null);
        assertEquals(StepResult.STATUS_PASSED, ok.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> bizOk = (Map<String, Object>) ok.getHttp().get("bizCheck");
        assertEquals(true, bizOk.get("passed"));

        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(), "{\"code\":200,\"msg\":\"not-success\"}")
        );
        GraphNode nodeFail = GraphNode.builder()
                .id("n-api-fail")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();
        StepResult fail = handler.execute(ctx, nodeFail, null);
        assertEquals(StepResult.STATUS_FAILED, fail.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), fail.getError().getCode());
        log("ok=" + ok.getStatus() + " fail=" + fail.getStatus());
        end("execute_apiBizCodeConfig_overridesDefault");
    }
}
