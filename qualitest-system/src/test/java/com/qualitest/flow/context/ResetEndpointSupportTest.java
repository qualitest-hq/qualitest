package com.qualitest.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 ResetEndpointSupport：由环境 URL 拼出 /test-support 根地址。
 * 边界：纯函数；覆盖 http 补路径、斜杠归一、JSON 多模块取默认、空基址。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ResetEndpointSupportTest
 */
class ResetEndpointSupportTest {

    /**
     * 前提：基址为普通 http，无 path。
     * 期望：末尾补上 /test-support。
     */
    @Test
    void resolve_httpBase_appendsTestSupport() {
        assertEquals(
                "http://localhost:8081/test-support",
                ResetEndpointSupport.resolve("http://localhost:8081")
        );
    }

    /**
     * 前提：基址末尾带斜杠。
     * 期望：归一后仍得到单一 /test-support，无双斜杠。
     */
    @Test
    void resolve_trimsTrailingSlash() {
        assertEquals(
                "http://localhost:8081/test-support",
                ResetEndpointSupport.resolve("http://localhost:8081/")
        );
    }

    /**
     * 前提：环境 URL 为多模块 JSON，含「默认模块」。
     * 期望：取默认模块基址再补 /test-support。
     */
    @Test
    void resolve_jsonModuleUsesDefaultModule() {
        String json = "{\"默认模块\":\"http://demo:8081\",\"其他\":\"http://other:9000\"}";
        assertEquals("http://demo:8081/test-support", ResetEndpointSupport.resolve(json));
    }

    /**
     * 前提：joinBaseAndPath 基址为空。
     * 期望：返回空串。
     */
    @Test
    void joinBaseAndPath_handlesEmptyBase() {
        assertEquals("", ResetEndpointSupport.joinBaseAndPath("", "/test-support"));
    }
}
