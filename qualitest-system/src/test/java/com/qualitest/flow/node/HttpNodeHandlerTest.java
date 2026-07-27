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
 * 测 HttpNodeHandler：绑定 API / 外联转发、extracts、preScript、业务码与 RunSession Cookie。
 * 边界：依赖 Mock，不发真实网络；存量 begin/end 保留。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpNodeHandlerTest
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
     * 前提：Mock 返回 200 + JSON；节点配置 JsonPath extracts。
     * 期望：passed；flow.token/code 提取成功；result.http 非空。
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
     * 前提：转发返回非 2xx。
     * 期望：failed；错误码 TF_HTTP_STATUS。
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
     * 前提：节点 data 缺少 testProjectApiId。
     * 期望：failed；错误码 TF_HTTP_UNBOUND。
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
     * 前提：API preRequestScript 调用 headers.add 写入 X-Flow。
     * 期望：转发参数含该头；passed；result.http.preScript 非空。
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
     * 前提：外联节点配 externalUrl，上下文已授权外联。
     * 期望：passed；extracts 写入 flow；callMode=external。
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
     * 前提：外联节点，但上下文未授权外联。
     * 期望：failed；错误码 TF_HTTP_EXTERNAL_DENIED。
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
     * 前提：外联节点带 preScript 添加 X-Ext。
     * 期望：passed；转发参数含 X-Ext；preScript 摘要非空。
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
     * 前提：useRunSession=true；响应含 Set-Cookie。
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
     * 前提：HTTP 200 但业务 code 非成功值。
     * 期望：failed；TF_BIZ_CODE；http.bizCheck.passed=false。
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
     * 前提：节点 successCheck.mode=off；body 含失败业务码。
     * 期望：跳过业务码校验，步骤仍可 passed。
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
     * 前提：外联节点；body 含失败业务码。
     * 期望：默认不校验业务码，步骤 passed。
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
     * 前提：API biz_code_config.successValues=[0]；分别测 code=0 与 code=200。
     * 期望：0 通过、200 失败。
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
