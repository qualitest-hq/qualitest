package com.qualitest.flow.node;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.api.util.ApiConfigTestFixtures;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.script.ApiRequestScriptService;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.AssetExtractPersistService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.HttpNodeHandler;
import com.qualitest.flow.script.ScriptRuntime;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.service.ITestProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 HttpNodeHandler：绑定 API / 外联转发、extracts（含 setCookie）、preScript、业务码、
 * 以及 retryUntil（多轮通过、单次 until 失败、状态码耗尽、硬错误不重试）。
 * 边界：依赖 Mock，不发真实网络。
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
        AssetExtractPersistService assetPersist = mock(AssetExtractPersistService.class);
        handler = new HttpNodeHandler(apiService, forwardService, scriptService, assetPersist);

        Map<String, Object> env = new HashMap<>();
        env.put("baseUrl", "http://localhost:8800");
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
    @DisplayName("项目 API：成功提取 flow 变量")
    void execute_success_extractsFlow() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_JSON_BODY)
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals("tok-1", ctx.getFlow().get("token"));
        assertEquals(0L, ((Number) ctx.getFlow().get("code")).longValue());
        assertNotNull(result.getHttp());
    }

    /**
     * 前提：转发返回非 2xx。
     * 期望：failed；错误码 TF_HTTP_STATUS。
     */
    @Test
    @Order(2)
    @DisplayName("项目 API：非 2xx 返回 TF_HTTP_STATUS")
    void execute_non2xx_fails() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
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
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_STATUS.getCode(), result.getError().getCode());
    }

    /**
     * 前提：节点 data 缺少 testProjectApiId。
     * 期望：failed；错误码 TF_HTTP_UNBOUND。
     */
    @Test
    @Order(3)
    @DisplayName("项目 API：未绑定 apiId 返回 TF_HTTP_UNBOUND")
    void execute_unboundApiId_fails() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        GraphNode node = GraphNode.builder().id("n1").type("http").data(data).build();
        StepResult result = handler.execute(ctx, node, null);
        assertEquals(FlowErrorCode.TF_HTTP_UNBOUND.getCode(), result.getError().getCode());
    }

    /**
     * 前提：API preRequestScript 调用 headers.add 写入 X-Flow。
     * 期望：转发参数含该头；passed；result.http.preScript 非空。
     */
    @Test
    @Order(4)
    @DisplayName("项目 API：前置脚本可添加请求头")
    void execute_preScriptAddsHeader() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .preRequestScript("api.request.headers.add({key:'X-Flow', value:'yes'});")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNotNull(result.getHttp().get("preScript"));
    }

    /**
     * 前提：外联节点配 externalUrl，上下文已授权外联。
     * 期望：passed；extracts 写入 flow；callMode=external。
     */
    @Test
    @Order(5)
    @DisplayName("外联：授权后成功并写入 extracts")
    void execute_external_success() {
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals("tok-ext", ctx.getFlow().get("token"));
        assertEquals("external", result.getHttp().get("callMode"));
    }

    /**
     * 前提：外联节点，但上下文未授权外联。
     * 期望：failed；错误码 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(6)
    @DisplayName("外联：未授权时返回 TF_HTTP_EXTERNAL_DENIED")
    void execute_external_deniedWhenNoPermission() {
        ctx.setExternalHttpPermitted(false);

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://oauth.example.com/token");
        data.put("httpMethod", "POST");
        GraphNode node = GraphNode.builder().id("n-denied").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED.getCode(), result.getError().getCode());
    }

    /**
     * 前提：外联节点带 preScript 添加 X-Ext。
     * 期望：passed；转发参数含 X-Ext；preScript 摘要非空。
     */
    @Test
    @Order(7)
    @DisplayName("外联：前置脚本可添加请求头")
    void execute_external_preScriptAddsHeader() {
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNotNull(result.getHttp().get("preScript"));
    }

    /**
     * 前提：登录响应含 Set-Cookie；节点 extracts 用 from=setCookie。
     * 期望：passed；flow 写入 Cookie 值。
     */
    @Test
    @Order(8)
    @DisplayName("提取：setCookie 写入 flow")
    void execute_setCookieExtract_writesFlow() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK",
                        Map.of("Set-Cookie", "JSESSIONID=abc; Path=/"), "{\"code\":200}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("extracts", List.of(Map.of(
                "from", "setCookie",
                "expr", "JSESSIONID",
                "name", "sid",
                "scope", "flow"
        )));
        GraphNode node = GraphNode.builder()
                .id("n-cookie")
                .type("http")
                .data(data)
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals("abc", ctx.getFlow().get("sid"));
    }

    /**
     * 前提：HTTP 200 但业务 code 非成功值。
     * 期望：failed；TF_BIZ_CODE；http.bizCheck.passed=false。
     */
    @Test
    @Order(9)
    @DisplayName("业务码：失败码导致步骤失败")
    void execute_bizCodeFail_failsStep() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
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
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("手机号或密码错误"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bizCheck = (Map<String, Object>) result.getHttp().get("bizCheck");
        assertNotNull(bizCheck);
        assertEquals(false, bizCheck.get("passed"));
        assertEquals(500, ((Number) bizCheck.get("actualCode")).intValue());
    }

    /**
     * 前提：节点 successCheck.mode=off；body 含失败业务码。
     * 期望：跳过业务码校验，步骤仍可 passed。
     */
    @Test
    @Order(10)
    @DisplayName("业务码：successCheck=off 时跳过校验")
    void execute_successCheckOff_skipsBizCode() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNull(result.getHttp().get("bizCheck"));
    }

    /**
     * 前提：外联节点；body 含失败业务码。
     * 期望：默认不校验业务码，步骤 passed。
     */
    @Test
    @Order(11)
    @DisplayName("外联：默认不校验业务码")
    void execute_external_skipsBizCodeByDefault() {
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNull(result.getHttp().get("bizCheck"));
    }

    /**
     * 前提：API biz_code_config.successValues=[0]；分别测 code=0 与 code=200。
     * 期望：0 通过、200 失败。
     */
    @Test
    @Order(12)
    @DisplayName("业务码：API 配置 successValues 覆盖默认")
    void execute_apiBizCodeConfig_overridesDefault() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
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
        assertEquals(RunStatus.PASSED.getCode(), ok.getStatus());
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
        assertEquals(RunStatus.FAILED.getCode(), fail.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), fail.getError().getCode());
    }

    /**
     * 前提：转发 401；节点 statusCheck.whitelist=[200,401]，successCheck=off。
     * 期望：步骤 passed；lastResponse.status=401；可供后续 Condition。
     */
    @Test
    @Order(13)
    @DisplayName("statusCheck=whitelist：401 仍 passed")
    void execute_whitelist_allows401() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/getInfo")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(401, "Unauthorized", Map.of(), "{\"msg\":\"auth\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("statusCheck", Map.of("mode", "whitelist", "values", List.of(200, 401)));
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-probe").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNull(result.getError());
        assertEquals(401, ctx.getLastResponse().getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> sc = (Map<String, Object>) result.getHttp().get("statusCheck");
        assertEquals("whitelist", sc.get("mode"));
        assertEquals(true, sc.get("passed"));
    }

    /**
     * 前提：转发 500；节点 statusCheck.whitelist=[200,401]。
     * 期望：failed；TF_HTTP_STATUS。
     */
    @Test
    @Order(14)
    @DisplayName("statusCheck=whitelist：500 仍失败")
    void execute_whitelist_rejects500() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/getInfo")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(500, "Error", Map.of(), "{\"msg\":\"err\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("statusCheck", Map.of("mode", "whitelist", "values", List.of(200, 401)));
        GraphNode node = GraphNode.builder().id("n-probe").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_STATUS.getCode(), result.getError().getCode());
    }

    /**
     * 前提：转发 503；节点 statusCheck.mode=off。
     * 期望：passed。
     */
    @Test
    @Order(15)
    @DisplayName("statusCheck=off：非 2xx 仍 passed")
    void execute_statusCheckOff_passes() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/getInfo")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(503, "Unavailable", Map.of(), "{}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("statusCheck", Map.of("mode", "off"));
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-off").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(503, ctx.getLastResponse().getStatus());
    }

    /**
     * 前提：HTTP 节点收到 Content-Type=image/png 且 bodyEncoding=base64。
     * 期望：步骤 http.response.bodyMedia.stored=false、kind=image；响应中无 bodyBase64 字段。
     */
    @Test
    @Order(20)
    @DisplayName("裸媒体响应写入 bodyMedia 标识且不存字节")
    void execute_binaryImage_writesBodyMediaMarker() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/captcha.png")
                .requestConfig(ApiConfigTestFixtures.REQUEST_JSON_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(
                        200, "OK",
                        Map.of("Content-Type", "image/png"),
                        "[binary image/png · 42 bytes]",
                        DebugHttpForwardResult.BODY_ENCODING_BASE64,
                        "AAAA")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-img").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.getHttp().get("response");
        assertNotNull(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMedia = (Map<String, Object>) response.get("bodyMedia");
        assertNotNull(bodyMedia);
        assertEquals("image", bodyMedia.get("kind"));
        assertEquals("image/png", bodyMedia.get("mime"));
        assertEquals(false, bodyMedia.get("stored"));
        assertFalse(response.containsKey("bodyBase64"));
    }

    /**
     * 前提：HTTP 200 + 业务失败 + Set-Cookie→asset；默认不抽。
     * 期望：TF_BIZ_CODE；ctx.asset 无脏 Session。
     */
    @Test
    @Order(16)
    @DisplayName("业务失败默认不写 asset extracts")
    void execute_bizFail_skipsAssetExtractByDefault() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK",
                        Map.of("Set-Cookie", "JSESSIONID=dirty-sid; Path=/"),
                        "{\"code\":500,\"msg\":\"用户不存在/密码错误\"}")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("extracts", List.of(Map.of(
                "from", "setCookie",
                "expr", "JSESSIONID",
                "scope", "asset",
                "entryKey", "adminAuth",
                "fieldPath", "JSESSIONID"
        )));
        GraphNode node = GraphNode.builder().id("n-dirty").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("500"));
        Object adminAuth = ctx.getAsset().get("adminAuth");
        assertTrue(adminAuth == null || (adminAuth instanceof Map<?, ?> m && m.get("JSESSIONID") == null));
    }

    /**
     * 前提：约定 codePath 带 $.；body 有明确业务码。
     * 期望：能解析到 actualCode，摘要不含「code=null」。
     */
    @Test
    @Order(17)
    @DisplayName("业务码：codePath 已含 $ 仍能解析")
    void execute_bizCodePathWithDollar_parsesActual() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/login")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(),
                        "{\"code\":0,\"msg\":\"手机号码不正确！\"}")
        );
        ctx.setProjectAuthConfig("""
                {"authProfiles":[{"id":"client","name":"客户端","match":{"pathPrefix":["/api"]},
                "headerName":"token","headerValueTemplate":"{{asset.clientAuth.token}}",
                "responseConvention":{"codePath":"$.code","successValues":[1],"messagePath":"$.msg","dataPath":"$.data"}}]}
                """);
        ctx.getAsset().put("clientAuth", new HashMap<>(Map.of("token", "dummy")));

        GraphNode node = GraphNode.builder()
                .id("n-dollar")
                .type("http")
                .data(Map.of("callMode", "project", "testProjectApiId", "1001"))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), result.getError().getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> bizCheck = (Map<String, Object>) result.getHttp().get("bizCheck");
        assertNotNull(bizCheck);
        assertNotNull(bizCheck.get("actualCode"), "应解析到业务码，勿因 codePath 带 $ 读飞；msg=" + result.getError().getMessage());
        assertEquals(0, ((Number) bizCheck.get("actualCode")).intValue());
        assertFalse(Boolean.TRUE.equals(bizCheck.get("parseMissed")));
        assertTrue(result.getError().getMessage().contains("0"));
        assertTrue(result.getError().getMessage().contains("手机号码不正确"));
    }

    /**
     * 前提：接口期望 json；转发 200 + HTML 登录页；successCheck=off。
     * 期望：步骤仍 passed；expectedMatch=false；responseKind=nonJson（供探活 Conditon 去登录）。
     */
    @Test
    @Order(18)
    @DisplayName("期望 json：200 HTML → expectedMatch=false")
    void execute_expectedJson_htmlBody_expectedMatchFalse() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/system/main/toDoList")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .responseConfig("{\"configVersion\":1,\"expectedResponseKind\":\"json\",\"responses\":[]}")
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(),
                        "<html><body>未登录或登录超时</body></html>")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("statusCheck", Map.of("mode", "whitelist", "values", List.of(200, 401, 403)));
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-probe-html").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(Boolean.FALSE, ctx.getLastResponse().getExpectedMatch());
        assertEquals("nonJson", ctx.getLastResponse().getResponseKind());
        assertEquals(false, result.getHttp().get("expectedMatch"));
        assertEquals("nonJson", result.getHttp().get("responseKind"));
    }

    /**
     * 前提：外联节点；转发 200，Content-Type=text/plain，正文为微信 v2 风格 &lt;xml&gt; 报文；extracts 取 $.prepay_id。
     * 期望：passed；body 转为对象可按字段取值；responseKind=xml；extracts 写入 flow；
     *       lastResponse.bodyText 与步骤报告 response.bodyText 保留原文。
     */
    @Test
    @Order(19)
    @DisplayName("XML 响应：自动转对象，原文保留")
    void execute_xmlResponse_parsedAndRawKept() {
        String xml = "<xml><return_code><![CDATA[SUCCESS]]></return_code>"
                + "<prepay_id><![CDATA[wx201410272009395522657a690389285100]]></prepay_id></xml>";
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "text/plain"), xml)
        );

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://api.mch.example.com/pay/unifiedorder");
        data.put("httpMethod", "POST");
        data.put("extracts", List.of(
                Map.of("from", "body", "expr", "$.prepay_id", "scope", "flow", "name", "prepayId")
        ));
        GraphNode node = GraphNode.builder().id("n-xml").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals("wx201410272009395522657a690389285100", ctx.getFlow().get("prepayId"));
        assertEquals("SUCCESS", ((Map<?, ?>) ctx.getLastResponse().getBody()).get("return_code"));
        assertEquals(xml, ctx.getLastResponse().getBodyText());
        assertEquals("xml", ctx.getLastResponse().getResponseKind());
        assertEquals("xml", result.getHttp().get("responseKind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.getHttp().get("response");
        assertEquals(xml, response.get("bodyText"));
    }

    /**
     * 前提：项目接口返回 application/xml；接口期望形态分别为 json 与 xml；successCheck=off。
     * 期望：期望 json 时 expectedMatch=false；期望 xml 时 expectedMatch=true；两次 responseKind 均为 xml。
     */
    @Test
    @Order(20)
    @DisplayName("XML 响应：按期望形态判定 expectedMatch")
    void execute_xmlResponse_expectedMatchByKind() {
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "application/xml"),
                        "<?xml version=\"1.0\"?><result><code>0</code></result>")
        );
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("successCheck", Map.of("mode", "off"));
        GraphNode node = GraphNode.builder().id("n-xml-kind").type("http").data(data).build();

        when(apiService.selectTestProjectApiById(1001L)).thenReturn(xmlApi("json"));
        StepResult jsonExpected = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), jsonExpected.getStatus());
        assertEquals(false, jsonExpected.getHttp().get("expectedMatch"));
        assertEquals("xml", jsonExpected.getHttp().get("responseKind"));

        when(apiService.selectTestProjectApiById(1001L)).thenReturn(xmlApi("xml"));
        StepResult xmlExpected = handler.execute(ctx, node, null);
        assertEquals(true, xmlExpected.getHttp().get("expectedMatch"));
        assertEquals("xml", xmlExpected.getHttp().get("responseKind"));
        assertEquals("0", ((Map<?, ?>) ctx.getLastResponse().getBody()).get("code"));
    }

    /**
     * 前提：外联节点显式 inherit；successValues=["SUCCESS"]；约定 codePath=return_code；
     *       分别返回 SUCCESS 与 FAIL 的 XML。
     * 期望：SUCCESS → passed；FAIL → TF_BIZ_CODE。
     */
    @Test
    @Order(21)
    @DisplayName("业务码：字符串 SUCCESS 可作白名单")
    void execute_stringBizCode_successAndFail() {
        ctx.setProjectAuthConfig("""
                {"authProfiles":[{"id":"wx","name":"微信","match":{"pathPrefix":["/pay"]},
                "headerName":"X-Unused","headerValueTemplate":"1",
                "responseConvention":{"codePath":"return_code","successValues":["SUCCESS"],
                "messagePath":"return_msg","dataPath":"data"}}]}
                """);

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://api.mch.example.com/pay/orderquery");
        data.put("httpMethod", "POST");
        data.put("successCheck", Map.of("mode", "inherit", "successValues", List.of("SUCCESS")));
        GraphNode node = GraphNode.builder().id("n-wx-biz").type("http").data(data).build();

        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "text/plain"),
                        "<xml><return_code><![CDATA[SUCCESS]]></return_code>"
                                + "<return_msg><![CDATA[OK]]></return_msg></xml>")
        );
        StepResult ok = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), ok.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> okBiz = (Map<String, Object>) ok.getHttp().get("bizCheck");
        assertNotNull(okBiz);
        assertEquals("SUCCESS", okBiz.get("actualCode"));
        assertEquals(List.of("SUCCESS"), okBiz.get("successValues"));

        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "text/plain"),
                        "<xml><return_code><![CDATA[FAIL]]></return_code>"
                                + "<return_msg><![CDATA[签名错误]]></return_msg></xml>")
        );
        StepResult fail = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), fail.getStatus());
        assertEquals(FlowErrorCode.TF_BIZ_CODE.getCode(), fail.getError().getCode());
        assertTrue(fail.getError().getMessage().contains("FAIL"));
    }

    /**
     * 前提：节点配置最多 3 次请求、间隔 0；通过条件要求 body.data.status=PAID；
     * Mock 首轮返回 PENDING、次轮返回 PAID；并配置 extracts 写入 flow.orderStatus。
     * 期望：步骤 passed；http.retry.attempts=2；flow.orderStatus=PAID。
     */
    @Test
    @Order(40)
    @DisplayName("retryUntil：PENDING 后再 PAID 第二轮通过")
    void execute_retryUntil_pendingThenPaid_passesOnSecondAttempt() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/order/status")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any()))
                .thenReturn(DebugHttpForwardResult.success(200, "OK",
                        Map.of("Content-Type", "application/json"),
                        "{\"code\":200,\"data\":{\"status\":\"PENDING\"}}"))
                .thenReturn(DebugHttpForwardResult.success(200, "OK",
                        Map.of("Content-Type", "application/json"),
                        "{\"code\":200,\"data\":{\"status\":\"PAID\"}}"));

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("successCheck", Map.of("mode", "off"));
        data.put("retryUntil", Map.of(
                "maxAttempts", 3,
                "intervalMs", 0,
                "until", List.of(Map.of(
                        "left", "http.body.data.status",
                        "operator", "eq",
                        "right", "PAID"))));
        data.put("extracts", List.of(
                Map.of("from", "body", "expr", "$.data.status", "scope", "flow", "name", "orderStatus")));
        GraphNode node = GraphNode.builder().id("n-retry").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals("PAID", ctx.getFlow().get("orderStatus"));
        assertNotNull(result.getHttp());
        @SuppressWarnings("unchecked")
        Map<String, Object> retry = (Map<String, Object>) result.getHttp().get("retry");
        assertNotNull(retry);
        assertEquals(2, ((Number) retry.get("attempts")).intValue());
        assertEquals(3, ((Number) retry.get("maxAttempts")).intValue());
    }

    /**
     * 前提：最多请求 1 次；通过条件要求 status=PAID；Mock 始终返回 PENDING。
     * 期望：步骤 failed、错误码 TF_ASSERT_FAILED；HTTP 只转发 1 次（不重试）。
     */
    @Test
    @Order(41)
    @DisplayName("retryUntil：默认 1 次 until 未过即失败")
    void execute_retryUntil_maxAttemptsOne_failsWithoutRetry() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/order/status")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(DebugHttpForwardResult.success(200, "OK",
                Map.of("Content-Type", "application/json"),
                "{\"code\":200,\"data\":{\"status\":\"PENDING\"}}"));

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("successCheck", Map.of("mode", "off"));
        data.put("retryUntil", Map.of(
                "maxAttempts", 1,
                "intervalMs", 0,
                "until", List.of(Map.of(
                        "left", "http.body.data.status",
                        "operator", "eq",
                        "right", "PAID"))));
        GraphNode node = GraphNode.builder().id("n-once").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED.getCode(), result.getError().getCode());
        org.mockito.Mockito.verify(forwardService, org.mockito.Mockito.times(1)).forward(any());
    }

    /**
     * 前提：最多请求 2 次、间隔 0；Mock 两轮均返回 HTTP 500。
     * 期望：步骤 failed、错误码 TF_HTTP_STATUS；实际转发 2 次；http.retry.attempts=2。
     */
    @Test
    @Order(42)
    @DisplayName("retryUntil：状态码失败耗尽次数")
    void execute_retryUntil_httpStatusExhausted_fails() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/flaky")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .build();
        when(apiService.selectTestProjectApiById(1001L)).thenReturn(api);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(500, "ERR", Map.of(), "{\"error\":true}"));

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1001");
        data.put("retryUntil", Map.of("maxAttempts", 2, "intervalMs", 0));
        GraphNode node = GraphNode.builder().id("n-5xx").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_STATUS.getCode(), result.getError().getCode());
        org.mockito.Mockito.verify(forwardService, org.mockito.Mockito.times(2)).forward(any());
        @SuppressWarnings("unchecked")
        Map<String, Object> retry = (Map<String, Object>) result.getHttp().get("retry");
        assertEquals(2, ((Number) retry.get("attempts")).intValue());
    }

    /**
     * 前提：最多请求 3 次；绑定的 testProjectApiId 在库中不存在。
     * 期望：步骤 failed、错误码 TF_HTTP_UNBOUND；不发起 HTTP 转发（硬错误不重试）。
     */
    @Test
    @Order(43)
    @DisplayName("retryUntil：硬错误不重试")
    void execute_retryUntil_hardError_noRetry() {
        when(apiService.selectTestProjectApiById(9999L)).thenReturn(null);
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "9999");
        data.put("retryUntil", Map.of("maxAttempts", 3, "intervalMs", 0));
        GraphNode node = GraphNode.builder().id("n-hard").type("http").data(data).build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_HTTP_UNBOUND.getCode(), result.getError().getCode());
        org.mockito.Mockito.verify(forwardService, org.mockito.Mockito.never()).forward(any());
    }

    /** 构造期望形态为 expectedKind 的项目接口 */
    private static TestProjectApi xmlApi(String expectedKind) {
        return TestProjectApi.builder()
                .testProjectApiId(1001L)
                .apiPath("/api/order/query")
                .requestConfig(ApiConfigTestFixtures.REQUEST_NONE_BODY)
                .responseConfig("{\"configVersion\":1,\"expectedResponseKind\":\"" + expectedKind + "\",\"responses\":[]}")
                .build();
    }
}

