package com.qualitest.api.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.script.ScriptRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ApiRequestScriptService：调试/流程 HTTP 的前后置脚本。
 * 边界：forwardService=null；空脚本跳过。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiRequestScriptServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiRequestScriptServiceTest {

    private final ScriptRuntime scriptRuntime = new ScriptRuntime(null);
    private final ApiRequestScriptService service = new ApiRequestScriptService(scriptRuntime, null);

    /**
     * 前提：前置脚本 headers.add('X-Test','1')。
     * 期望：params.headers 含该头；result.success=true。
     */
    @Test
    @Order(1)
    @DisplayName("前置脚本：可添加自定义请求头")
    void preScript_addHeader() {
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
        assertTrue(result.isSuccess(), "前置脚本应执行成功");
        assertEquals("1", params.getHeaders().stream()
                .filter(h -> "X-Test".equals(h.getName()))
                .findFirst()
                .map(DebugHttpForwardParams.HeaderPair::getValue)
                .orElse(null), "应写入自定义请求头 X-Test=1");
    }

    /**
     * 前提：前置脚本 variables.set('token','abc')。
     * 期望：variables.token=abc。
     */
    @Test
    @Order(2)
    @DisplayName("前置脚本：可写入 variables")
    void preScript_setVariable() {
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
        assertTrue(result.isSuccess(), "前置脚本应执行成功");
        assertEquals("abc", variables.get("token"), "应写入 variables.token=abc");
    }

    /**
     * 前提：后置脚本断言 response.code==200，实际 200。
     * 期望：success；tests 首项 passed=true。
     */
    @Test
    @Order(3)
    @DisplayName("后置脚本：断言通过时 success 且记录 passed")
    void postScript_assertPass() {
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
        assertTrue(result.isSuccess(), "response.code=200 时断言应通过");
        assertFalse(result.getTests().isEmpty(), "应记录一条 api.test 结果");
        assertTrue((Boolean) result.getTests().get(0).get("passed"), "首条测试应为 passed=true");
    }

    /**
     * 前提：后置脚本断言 code==200，实际 500。
     * 期望：失败；错误码 TF_ASSERT_FAILED。
     */
    @Test
    @Order(4)
    @DisplayName("后置脚本：断言失败时返回 TF_ASSERT_FAILED")
    void postScript_assertFail() {
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
        assertFalse(result.isSuccess(), "response.code=500 时断言应失败");
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED, result.getErrorCode(), "失败错误码应为 TF_ASSERT_FAILED");
    }

    /**
     * 前提：脚本为 null/空白。
     * 期望：直接 success，不改动请求快照与 variables。
     */
    @Test
    @Order(5)
    @DisplayName("空脚本：跳过执行且不改上下文")
    void blankScript_skips() {
        ApiScriptContext context = new ApiScriptContext();
        context.mergeScopeMaps(new HashMap<>(Map.of("token", "abc")), new HashMap<>(), new HashMap<>());
        context.getRequest().setMethod("GET");
        context.getRequest().setUrl("http://localhost/api");

        ApiScriptExecutionResult result = service.executePre("", context);

        assertTrue(result.isSuccess(), "空脚本应视为跳过成功");
        assertEquals("abc", context.getVariables().get("token"), "空脚本不应改动 variables");
        assertEquals("GET", context.getRequest().getMethod(), "空脚本不应改动请求 method");
        assertEquals("http://localhost/api", context.getRequest().getUrl(), "空脚本不应改动请求 url");
    }
}
