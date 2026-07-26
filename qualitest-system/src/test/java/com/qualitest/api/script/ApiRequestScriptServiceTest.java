package com.qualitest.api.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.script.ScriptRuntime;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ApiRequestScriptService} 单元测试：验证 API 调试/流程 HTTP 步的前置与后置脚本执行。
 * <p>
 * 被测对象在 HTTP 转发前执行 preRequestScript（可改写 headers、variables），
 * 转发后执行 postRequestScript（含 api.test 断言），返回 {@link ApiScriptExecutionResult}。
 * forwardService 传 null，不涉及真实 HTTP。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ApiRequestScriptServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiRequestScriptServiceTest {

    private final ScriptRuntime scriptRuntime = new ScriptRuntime(null);
    private final ApiRequestScriptService service = new ApiRequestScriptService(scriptRuntime, null);

    /**
     * 前置脚本 {@code api.request.headers.add} 应向 DebugHttpForwardParams 添加自定义请求头。
     * 期望：params.headers 含 X-Test=1；result.success=true。
     */
    @Test
    @Order(1)
    void preScript_addHeader() {
        begin("preScript_addHeader");
        DebugHttpForwardParams params = DebugHttpForwardParams.builder()
                .method("GET")
                .url("http://localhost/api")
                .build();
        ApiScriptExecutionResult result = service.executePreIfPresent(
                "api.request.headers.add({key:'X-Test', value:'1'});",
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                params
        );
        assertTrue(result.isSuccess());
        assertEquals("1", params.getHeaders().stream()
                .filter(h -> "X-Test".equals(h.getName()))
                .findFirst()
                .map(DebugHttpForwardParams.HeaderPair::getValue)
                .orElse(null));
        log("header X-Test=1");
        end("preScript_addHeader");
    }

    /**
     * 前置脚本 {@code api.variables.set('token', 'abc')} 应写入 variables Map。
     * 期望：variables.get("token") == "abc"。
     */
    @Test
    @Order(2)
    void preScript_setVariable() {
        begin("preScript_setVariable");
        Map<String, Object> variables = new HashMap<>();
        DebugHttpForwardParams params = DebugHttpForwardParams.builder()
                .method("POST")
                .url("http://localhost/api")
                .build();
        ApiScriptExecutionResult result = service.executePreIfPresent(
                "api.variables.set('token', 'abc');",
                variables,
                new HashMap<>(),
                new HashMap<>(),
                params
        );
        assertTrue(result.isSuccess());
        assertEquals("abc", variables.get("token"));
        log("token=abc");
        end("preScript_setVariable");
    }

    /**
     * 后置脚本 api.test 断言 response.code==200 通过时。
     * 期望：result.success；tests 列表首项 passed=true。
     */
    @Test
    @Order(3)
    void postScript_assertPass() {
        begin("postScript_assertPass");
        ApiScriptContext.ResponseSnapshot response = ApiScriptSupport.fromForwardResult(
                200, "OK", Map.of("Content-Type", "application/json"), "{\"ok\":true}", 10L);
        DebugHttpForwardParams params = DebugHttpForwardParams.builder()
                .method("GET")
                .url("http://localhost/api")
                .build();
        ApiScriptExecutionResult result = service.executePostIfPresent(
                """
                        api.test('status is 200', function () {
                          api.expect(api.response.code).to.equal(200);
                        });
                        """,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                params,
                response
        );
        assertTrue(result.isSuccess());
        assertFalse(result.getTests().isEmpty());
        assertTrue((Boolean) result.getTests().get(0).get("passed"));
        log("testsPassed=1");
        end("postScript_assertPass");
    }

    /**
     * 后置脚本断言 response.code==200 但实际返回 500 时。
     * 期望：result 失败，错误码 {@link FlowErrorCode#TF_ASSERT_FAILED}。
     */
    @Test
    @Order(4)
    void postScript_assertFail() {
        begin("postScript_assertFail");
        ApiScriptContext.ResponseSnapshot response = ApiScriptSupport.fromForwardResult(
                500, "Error", Map.of(), "{}", 10L);
        DebugHttpForwardParams params = DebugHttpForwardParams.builder()
                .method("GET")
                .url("http://localhost/api")
                .build();
        ApiScriptExecutionResult result = service.executePostIfPresent(
                "api.test('status is 200', function () { api.expect(api.response.code).to.equal(200); });",
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                params,
                response
        );
        assertFalse(result.isSuccess());
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED, result.getErrorCode());
        log("errorCode=" + result.getErrorCode().getCode());
        end("postScript_assertFail");
    }

    /**
     * 空/空白脚本应跳过执行，直接返回 success（不报错）。
     * 期望：result.success=true。
     */
    @Test
    @Order(5)
    void blankScript_skips() {
        begin("blankScript_skips");
        ApiScriptContext context = new ApiScriptContext();
        ApiScriptExecutionResult result = service.executePre("", context);
        assertTrue(result.isSuccess());
        log("skipped=true");
        end("blankScript_skips");
    }
}
