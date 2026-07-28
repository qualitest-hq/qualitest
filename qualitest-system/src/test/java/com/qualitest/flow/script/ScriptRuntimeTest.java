package com.qualitest.flow.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 ScriptRuntime：JS/Python 沙箱执行、内置工具与超时/非法语言。
 * 边界：真实 Graal 引擎；含死循环超时；ctx.http 用 Mock ForwardService。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ScriptRuntimeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptRuntimeTest {

    private final ScriptRuntime runtime = new ScriptRuntime(null);

    /**
     * 前提：flow.a 已有值；JS getFlow/setFlow 写入 x。
     * 期望：成功；flow.x=原 a；writes 记录本次写入。
     */
    @Test
    @Order(1)
    @DisplayName("JS getFlow/setFlow 写入成功")
    void javascript_setFlowAndGetFlow() {
        FlowRunContext ctx = FlowRunContext.builder()
                .flow(new HashMap<>(Map.of("a", "hello")))
                .build();
        String source = "ctx.setFlow('x', ctx.getFlow('a'));";
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                source,
                5000,
                ctx
        );
        assertTrue(result.isSuccess());
        assertEquals("hello", ctx.getFlow().get("x"));
        assertEquals(1, result.getWrites().size());
        assertEquals("x", result.getWrites().get(0).get("key"));
    }

    /**
     * 前提：JS 调用 jsonParse / hmacSha256 / md5。
     * 期望：HMAC 64 位十六进制；MD5 与已知值一致。
     */
    @Test
    @Order(2)
    @DisplayName("JS jsonParse 与 HMAC/MD5 正确")
    void javascript_jsonAndHmac() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        String source = """
                var obj = ctx.jsonParse('{"n":1}');
                ctx.setFlow('sign', ctx.hmacSha256('data', 'secret'));
                ctx.setFlow('digest', ctx.md5('data'));
                """;
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                source,
                5000,
                ctx
        );
        assertTrue(result.isSuccess());
        assertNotNull(ctx.getFlow().get("sign"));
        assertEquals(64, String.valueOf(ctx.getFlow().get("sign")).length());
        assertEquals("636a83b0e93f990608f25e7798322c6", ctx.getFlow().get("digest"));
    }

    /**
     * 前提：Python 脚本 setFlow('pyOk', True)。
     * 期望：成功；flow.pyOk=true。
     */
    @Test
    @Order(3)
    @DisplayName("Python setFlow 冒烟成功")
    void python_smokeSetFlow() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        String source = "ctx.setFlow('pyOk', True)";
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_PYTHON,
                source,
                5000,
                ctx
        );
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(true, ctx.getFlow().get("pyOk"));
    }

    /**
     * 前提：源码为空或仅空白。
     * 期望：失败；错误码 TF_SCRIPT_ERROR。
     */
    @Test
    @Order(4)
    @DisplayName("空源码失败并返回 TF_SCRIPT_ERROR")
    void emptySource_fails() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                "  ",
                5000,
                ctx
        );
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR, result.getErrorCode());
    }

    /**
     * 前提：language=ruby。
     * 期望：失败；错误码 TF_SCRIPT_ERROR。
     */
    @Test
    @Order(5)
    @DisplayName("非法语言失败并返回 TF_SCRIPT_ERROR")
    void invalidLanguage_fails() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        ScriptExecutionResult result = runtime.execute("ruby", "1", 5000, ctx);
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR, result.getErrorCode());
    }

    /**
     * 前提：while(true){}，timeoutMs=200。
     * 期望：失败；错误码 TF_SCRIPT_TIMEOUT。
     */
    @Test
    @Order(6)
    @DisplayName("死循环超时返回 TF_SCRIPT_TIMEOUT")
    void timeout_fails() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        String source = "while(true){}";
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                source,
                200,
                ctx
        );
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_TIMEOUT, result.getErrorCode());
    }

    /**
     * 前提：JS 调用 base64/uuid/log/session。
     * 期望：编解码往返；uuid 非空；session cached=token-1；logs 含 hello。
     */
    @Test
    @Order(7)
    @DisplayName("JS base64/uuid/session/log 可用")
    void javascript_enhancedCtxApis() {
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).session(new HashMap<>()).build();
        String source = """
                ctx.log('hello');
                ctx.setFlow('b64', ctx.base64Encode('ok'));
                ctx.setFlow('raw', ctx.base64Decode(ctx.getFlow('b64')));
                ctx.setFlow('id', ctx.uuid());
                ctx.session.set('cached', 'token-1');
                ctx.setFlow('cached', ctx.session.get('cached'));
                """;
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                source,
                5000,
                ctx
        );
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("b2s=", ctx.getFlow().get("b64"));
        assertEquals("ok", ctx.getFlow().get("raw"));
        assertNotNull(ctx.getFlow().get("id"));
        assertEquals("token-1", ctx.getFlow().get("cached"));
        assertTrue(result.getLogs().stream().anyMatch(l -> l.contains("hello")));
    }

    /**
     * 前提：ctx.http 外联 Mock 返回 JSON。
     * 期望：flow.ok=true；flow.code=abc。
     */
    @Test
    @Order(8)
    @DisplayName("ctx.http 成功写入 flow")
    void javascript_ctxHttp_success() {
        IDebugHttpForwardService forwardService = mock(IDebugHttpForwardService.class);
        when(forwardService.forward(any(DebugHttpForwardParams.class))).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of("Content-Type", "application/json"),
                        "{\"code\":\"abc\"}")
        );
        ScriptRuntime httpRuntime = new ScriptRuntime(forwardService);
        FlowRunContext ctx = FlowRunContext.builder()
                .env(new HashMap<>(Map.of("tokenUrl", "https://oauth.example.com/token")))
                .flow(new HashMap<>())
                .build();
        String source = """
                var res = ctx.http({
                  method: 'POST',
                  url: ctx.getEnv('tokenUrl'),
                  body: 'grant_type=client_credentials'
                });
                ctx.setFlow('ok', res.ok);
                ctx.setFlow('code', ctx.jsonParse(res.body).code);
                """;
        ScriptExecutionResult result = httpRuntime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                source,
                5000,
                ctx
        );
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(true, ctx.getFlow().get("ok"));
        assertEquals("abc", ctx.getFlow().get("code"));
    }

    /**
     * 前提：单步 ctx.http 调用次数超过上限。
     * 期望：success=false；错误码 TF_SCRIPT_ERROR。
     */
    @Test
    @Order(9)
    @DisplayName("ctx.http 超限调用失败")
    void javascript_ctxHttp_callLimit() {
        IDebugHttpForwardService forwardService = mock(IDebugHttpForwardService.class);
        when(forwardService.forward(any())).thenReturn(
                DebugHttpForwardResult.success(200, "OK", Map.of(), "{}")
        );
        ScriptRuntime httpRuntime = new ScriptRuntime(forwardService);
        FlowRunContext ctx = FlowRunContext.builder()
                .env(new HashMap<>(Map.of("u", "https://oauth.example.com/x")))
                .flow(new HashMap<>())
                .build();
        int limit = ScriptConstants.MAX_HTTP_CALLS_PER_SCRIPT;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= limit; i++) {
            sb.append("ctx.http({ method: 'GET', url: ctx.getEnv('u') });\n");
        }
        ScriptExecutionResult result = httpRuntime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                sb.toString(),
                5000,
                ctx
        );
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR, result.getErrorCode());
    }
}
