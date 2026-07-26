package com.qualitest.flow.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
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
 * {@link ScriptRuntime} 单元测试：验证流程脚本引擎对 JavaScript / Python 的执行能力。
 * <p>
 * 被测对象负责：在隔离沙箱中执行用户脚本，向脚本暴露 {@code ctx} 对象（读写 flow 变量、
 * JSON 解析、HMAC/MD5 等工具方法），并返回 {@link ScriptExecutionResult}（成功/失败、写入记录、错误码）。
 * <p>
 * 覆盖场景：JS 读写 flow、内置加密函数、Python 基本执行、空脚本/非法语言/超时等异常路径。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ScriptRuntimeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptRuntimeTest {

    private final ScriptRuntime runtime = new ScriptRuntime(null);

    /**
     * JavaScript 脚本通过 {@code ctx.getFlow} 读取已有变量，再用 {@code ctx.setFlow} 写入新变量。
     * 期望：执行成功；{@code flow.x} 等于原 {@code flow.a} 的值；{@code writes} 列表记录本次写入。
     */
    @Test
    @Order(1)
    void javascript_setFlowAndGetFlow() {
        begin("javascript_setFlowAndGetFlow");
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
        log("flow.x=" + ctx.getFlow().get("x"));
        end("javascript_setFlowAndGetFlow");
    }

    /**
     * JavaScript 内置工具方法：{@code ctx.jsonParse} 解析 JSON 字符串；
     * {@code ctx.hmacSha256}、{@code ctx.md5} 计算摘要。
     * 期望：HMAC 输出 64 位十六进制；MD5 与已知值一致。
     */
    @Test
    @Order(2)
    void javascript_jsonAndHmac() {
        begin("javascript_jsonAndHmac");
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
        log("sign=" + ctx.getFlow().get("sign"));
        end("javascript_jsonAndHmac");
    }

    /**
     * Python 脚本冒烟：{@code ctx.setFlow('pyOk', True)} 写入布尔值。
     * 期望：执行成功；{@code flow.pyOk == true}。
     */
    @Test
    @Order(3)
    void python_smokeSetFlow() {
        begin("python_smokeSetFlow");
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
        log("flow.pyOk=" + ctx.getFlow().get("pyOk"));
        end("python_smokeSetFlow");
    }

    /**
     * 脚本源码为空或仅空白字符时不应执行。
     * 期望：失败，错误码 {@link FlowErrorCode#TF_SCRIPT_ERROR}。
     */
    @Test
    @Order(4)
    void emptySource_fails() {
        begin("emptySource_fails");
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        ScriptExecutionResult result = runtime.execute(
                ScriptConstants.LANGUAGE_JAVASCRIPT,
                "  ",
                5000,
                ctx
        );
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR, result.getErrorCode());
        log("errorCode=" + result.getErrorCode().getCode());
        end("emptySource_fails");
    }

    /**
     * 传入不支持的语言标识（如 ruby）时应拒绝执行。
     * 期望：失败，错误码 {@link FlowErrorCode#TF_SCRIPT_ERROR}。
     */
    @Test
    @Order(5)
    void invalidLanguage_fails() {
        begin("invalidLanguage_fails");
        FlowRunContext ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
        ScriptExecutionResult result = runtime.execute("ruby", "1", 5000, ctx);
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR, result.getErrorCode());
        log("errorCode=" + result.getErrorCode().getCode());
        end("invalidLanguage_fails");
    }

    /**
     * 脚本执行超过 {@code timeoutMs} 限制时应被强制中断。
     * 本用例脚本为 {@code while(true){}} 死循环，超时设为 200ms。
     * 期望：失败，错误码 {@link FlowErrorCode#TF_SCRIPT_TIMEOUT}。
     */
    @Test
    @Order(6)
    void timeout_fails() {
        begin("timeout_fails");
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
        log("errorCode=" + result.getErrorCode().getCode());
        end("timeout_fails");
    }

    /**
     * base64 / uuid / log / session API。
     * 期望：base64 编解码往返；uuid 非空；session 读写 cached=token-1；logs 含 hello。
     */
    @Test
    @Order(7)
    void javascript_enhancedCtxApis() {
        begin("javascript_enhancedCtxApis");
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
        log("b64RoundTrip=ok sessionCached=token-1");
        end("javascript_enhancedCtxApis");
    }

    /**
     * ctx.http 外联：返回 ok/status/body。
     * 期望：flow.ok=true；flow.code=abc（从响应 JSON 解析）。
     */
    @Test
    @Order(8)
    void javascript_ctxHttp_success() {
        begin("javascript_ctxHttp_success");
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
        log("ok=true code=abc");
        end("javascript_ctxHttp_success");
    }

    /**
     * ctx.http 单步调用次数超过上限时应失败。
     * 期望：success=false；错误码 {@link FlowErrorCode#TF_SCRIPT_ERROR}。
     */
    @Test
    @Order(9)
    void javascript_ctxHttp_callLimit() {
        begin("javascript_ctxHttp_callLimit");
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
        log("callLimitExceeded=true");
        end("javascript_ctxHttp_callLimit");
    }
}
